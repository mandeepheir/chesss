import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chess with full rules: all pieces, turns, captures, check / checkmate /
 * stalemate, castling, en passant, pawn promotion, undo, and board flip.
 *
 * Piece images are optional. If Resources/ contains all 12 images
 * (WP WKN WB WR WQ WK BP BKN BB BR BQ BK, .png) they are used, otherwise
 * the game draws Unicode chess symbols instead.
 */
public class Chess {

    // =====================================================================
    //  MODEL
    // =====================================================================

    static final char EMPTY = '.';

    static final int[][] KNIGHT = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    static final int[][] DIAG = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    static final int[][] KING = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    /** A single move. promo is 0 when the move is not a promotion. */
    static final class Move {
        final int fr, fc, tr, tc;
        final char promo;
        final boolean enPassant, castle;

        Move(int fr, int fc, int tr, int tc, char promo, boolean enPassant, boolean castle) {
            this.fr = fr;
            this.fc = fc;
            this.tr = tr;
            this.tc = tc;
            this.promo = promo;
            this.enPassant = enPassant;
            this.castle = castle;
        }
    }

    /** Full game position. Row 0 is Black's back rank, row 7 is White's. */
    static final class State {
        char[][] b = new char[8][8];
        boolean whiteTurn = true;
        boolean wK = true, wQ = true, bK = true, bQ = true; // castling rights
        int epRow = -1, epCol = -1;                          // en passant target square
        Move last;

        State copy() {
            State s = new State();
            for (int r = 0; r < 8; r++) {
                s.b[r] = b[r].clone();
            }
            s.whiteTurn = whiteTurn;
            s.wK = wK;
            s.wQ = wQ;
            s.bK = bK;
            s.bQ = bQ;
            s.epRow = epRow;
            s.epCol = epCol;
            s.last = last;
            return s;
        }

        static State initial() {
            State s = new State();
            String back = "rnbqkbnr";
            for (int c = 0; c < 8; c++) {
                s.b[0][c] = back.charAt(c);
                s.b[1][c] = 'p';
                s.b[6][c] = 'P';
                s.b[7][c] = Character.toUpperCase(back.charAt(c));
                for (int r = 2; r < 6; r++) {
                    s.b[r][c] = EMPTY;
                }
            }
            return s;
        }
    }

    static boolean isWhite(char p) {
        return Character.isUpperCase(p);
    }

    static boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    // ---------- attack detection ----------

    static boolean isAttacked(char[][] b, int r, int c, boolean byWhite) {
        // Pawns
        int pr = byWhite ? r + 1 : r - 1;
        char pawn = byWhite ? 'P' : 'p';
        for (int dc = -1; dc <= 1; dc += 2) {
            if (inBounds(pr, c + dc) && b[pr][c + dc] == pawn) return true;
        }
        // Knights
        char knight = byWhite ? 'N' : 'n';
        for (int[] d : KNIGHT) {
            int nr = r + d[0], nc = c + d[1];
            if (inBounds(nr, nc) && b[nr][nc] == knight) return true;
        }
        // King
        char king = byWhite ? 'K' : 'k';
        for (int[] d : KING) {
            int nr = r + d[0], nc = c + d[1];
            if (inBounds(nr, nc) && b[nr][nc] == king) return true;
        }
        // Sliding pieces
        char rook = byWhite ? 'R' : 'r';
        char bishop = byWhite ? 'B' : 'b';
        char queen = byWhite ? 'Q' : 'q';
        for (int[] d : ORTHO) {
            int nr = r + d[0], nc = c + d[1];
            while (inBounds(nr, nc)) {
                char p = b[nr][nc];
                if (p != EMPTY) {
                    if (p == rook || p == queen) return true;
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
        for (int[] d : DIAG) {
            int nr = r + d[0], nc = c + d[1];
            while (inBounds(nr, nc)) {
                char p = b[nr][nc];
                if (p != EMPTY) {
                    if (p == bishop || p == queen) return true;
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
        return false;
    }

    static int[] findKing(char[][] b, boolean white) {
        char k = white ? 'K' : 'k';
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (b[r][c] == k) return new int[]{r, c};
            }
        }
        return null;
    }

    static boolean inCheck(State s, boolean white) {
        int[] k = findKing(s.b, white);
        return k != null && isAttacked(s.b, k[0], k[1], !white);
    }

    // ---------- move generation ----------

    static List<Move> pseudoMoves(State s) {
        List<Move> out = new ArrayList<>();
        boolean white = s.whiteTurn;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char p = s.b[r][c];
                if (p == EMPTY || isWhite(p) != white) continue;
                switch (Character.toLowerCase(p)) {
                    case 'p':
                        pawnMoves(s, r, c, white, out);
                        break;
                    case 'n':
                        stepMoves(s, r, c, white, KNIGHT, out);
                        break;
                    case 'b':
                        slideMoves(s, r, c, white, DIAG, out);
                        break;
                    case 'r':
                        slideMoves(s, r, c, white, ORTHO, out);
                        break;
                    case 'q':
                        slideMoves(s, r, c, white, DIAG, out);
                        slideMoves(s, r, c, white, ORTHO, out);
                        break;
                    case 'k':
                        stepMoves(s, r, c, white, KING, out);
                        castleMoves(s, r, c, white, out);
                        break;
                    default:
                        break;
                }
            }
        }
        return out;
    }

    static void addPawnMove(int fr, int fc, int tr, int tc, boolean white, boolean ep, List<Move> out) {
        int promoRow = white ? 0 : 7;
        if (tr == promoRow) {
            String promos = white ? "QRBN" : "qrbn";
            for (char pc : promos.toCharArray()) {
                out.add(new Move(fr, fc, tr, tc, pc, false, false));
            }
        } else {
            out.add(new Move(fr, fc, tr, tc, (char) 0, ep, false));
        }
    }

    static void pawnMoves(State s, int r, int c, boolean white, List<Move> out) {
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;
        int nr = r + dir;
        if (inBounds(nr, c) && s.b[nr][c] == EMPTY) {
            addPawnMove(r, c, nr, c, white, false, out);
            int nr2 = r + 2 * dir;
            if (r == startRow && s.b[nr2][c] == EMPTY) {
                out.add(new Move(r, c, nr2, c, (char) 0, false, false));
            }
        }
        for (int dc = -1; dc <= 1; dc += 2) {
            int nc = c + dc;
            if (!inBounds(nr, nc)) continue;
            char t = s.b[nr][nc];
            if (t != EMPTY && isWhite(t) != white) {
                addPawnMove(r, c, nr, nc, white, false, out);
            } else if (t == EMPTY && nr == s.epRow && nc == s.epCol) {
                out.add(new Move(r, c, nr, nc, (char) 0, true, false));
            }
        }
    }

    static void stepMoves(State s, int r, int c, boolean white, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (!inBounds(nr, nc)) continue;
            char t = s.b[nr][nc];
            if (t == EMPTY || isWhite(t) != white) {
                out.add(new Move(r, c, nr, nc, (char) 0, false, false));
            }
        }
    }

    static void slideMoves(State s, int r, int c, boolean white, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (inBounds(nr, nc)) {
                char t = s.b[nr][nc];
                if (t == EMPTY) {
                    out.add(new Move(r, c, nr, nc, (char) 0, false, false));
                } else {
                    if (isWhite(t) != white) {
                        out.add(new Move(r, c, nr, nc, (char) 0, false, false));
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    static void castleMoves(State s, int r, int c, boolean white, List<Move> out) {
        int row = white ? 7 : 0;
        if (r != row || c != 4) return;
        char king = white ? 'K' : 'k';
        char rook = white ? 'R' : 'r';
        if (s.b[row][4] != king) return;
        if (isAttacked(s.b, row, 4, !white)) return;

        boolean kingSide = white ? s.wK : s.bK;
        boolean queenSide = white ? s.wQ : s.bQ;

        if (kingSide && s.b[row][7] == rook && s.b[row][5] == EMPTY && s.b[row][6] == EMPTY
                && !isAttacked(s.b, row, 5, !white) && !isAttacked(s.b, row, 6, !white)) {
            out.add(new Move(row, 4, row, 6, (char) 0, false, true));
        }
        if (queenSide && s.b[row][0] == rook && s.b[row][1] == EMPTY && s.b[row][2] == EMPTY
                && s.b[row][3] == EMPTY
                && !isAttacked(s.b, row, 3, !white) && !isAttacked(s.b, row, 2, !white)) {
            out.add(new Move(row, 4, row, 2, (char) 0, false, true));
        }
    }

    /** Apply a move and return the new position. */
    static State apply(State s, Move m) {
        State n = s.copy();
        char piece = n.b[m.fr][m.fc];

        if (m.enPassant) {
            n.b[m.fr][m.tc] = EMPTY; // captured pawn sits beside the mover
        }
        n.b[m.tr][m.tc] = (m.promo != 0) ? m.promo : piece;
        n.b[m.fr][m.fc] = EMPTY;

        if (m.castle) {
            if (m.tc == 6) {
                n.b[m.tr][5] = n.b[m.tr][7];
                n.b[m.tr][7] = EMPTY;
            } else {
                n.b[m.tr][3] = n.b[m.tr][0];
                n.b[m.tr][0] = EMPTY;
            }
        }

        // Castling rights
        if (piece == 'K') {
            n.wK = false;
            n.wQ = false;
        }
        if (piece == 'k') {
            n.bK = false;
            n.bQ = false;
        }
        if ((m.fr == 7 && m.fc == 0) || (m.tr == 7 && m.tc == 0)) n.wQ = false;
        if ((m.fr == 7 && m.fc == 7) || (m.tr == 7 && m.tc == 7)) n.wK = false;
        if ((m.fr == 0 && m.fc == 0) || (m.tr == 0 && m.tc == 0)) n.bQ = false;
        if ((m.fr == 0 && m.fc == 7) || (m.tr == 0 && m.tc == 7)) n.bK = false;

        // En passant target
        n.epRow = -1;
        n.epCol = -1;
        if ((piece == 'P' || piece == 'p') && Math.abs(m.tr - m.fr) == 2) {
            n.epRow = (m.fr + m.tr) / 2;
            n.epCol = m.fc;
        }

        n.whiteTurn = !s.whiteTurn;
        n.last = m;
        return n;
    }

    /** All fully legal moves (own king never left in check). */
    static List<Move> legalMoves(State s) {
        List<Move> legal = new ArrayList<>();
        for (Move m : pseudoMoves(s)) {
            State n = apply(s, m);
            int[] k = findKing(n.b, s.whiteTurn);
            if (k != null && !isAttacked(n.b, k[0], k[1], !s.whiteTurn)) {
                legal.add(m);
            }
        }
        return legal;
    }

    static boolean insufficientMaterial(State s) {
        int minors = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char p = Character.toLowerCase(s.b[r][c]);
                if (p == EMPTY || p == 'k') continue;
                if (p == 'n' || p == 'b') {
                    minors++;
                } else {
                    return false; // pawn, rook or queen on board
                }
            }
        }
        return minors <= 1;
    }

    // =====================================================================
    //  VIEW / CONTROLLER
    // =====================================================================

    static final class BoardPanel extends JPanel {

        private static final Color LIGHT = new Color(240, 217, 181);
        private static final Color DARK = new Color(181, 136, 99);
        private static final Color SELECT = new Color(246, 246, 105, 190);
        private static final Color LAST = new Color(205, 210, 106, 140);
        private static final Map<Character, String> GLYPHS = new HashMap<>();

        static {
            GLYPHS.put('k', "\u265A");
            GLYPHS.put('q', "\u265B");
            GLYPHS.put('r', "\u265C");
            GLYPHS.put('b', "\u265D");
            GLYPHS.put('n', "\u265E");
            GLYPHS.put('p', "\u265F");
        }

        private final JLabel statusLabel;
        private final Map<Character, Image> images = new HashMap<>();
        private final Deque<State> history = new ArrayDeque<>();

        private State state = State.initial();
        private List<Move> legal = new ArrayList<>();
        private List<Move> selMoves = new ArrayList<>();
        private int selR = -1, selC = -1;
        private boolean flipped = false;
        private boolean gameOver = false;

        private int sq = 80, bx = 0, by = 0; // board geometry

        BoardPanel(JLabel statusLabel) {
            this.statusLabel = statusLabel;
            loadImages();
            setBackground(new Color(48, 46, 43));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onClick(e.getX(), e.getY());
                }
            });
            refresh();
        }

        // ---------- images ----------

        private void loadImages() {
            for (char type : "PNBRQK".toCharArray()) {
                for (char colour : "WB".toCharArray()) {
                    String name = (type == 'N') ? "KN" : String.valueOf(type); // knights are WKN / BKN
                    File f = new File("Resources/" + colour + name + ".png");
                    if (f.exists()) {
                        ImageIcon icon = new ImageIcon(f.getPath());
                        if (icon.getIconWidth() > 0) {
                            char key = (colour == 'W') ? type : Character.toLowerCase(type);
                            images.put(key, icon.getImage());
                        }
                    }
                }
            }
            if (images.size() < 12) {
                images.clear(); // incomplete set, so use symbols for every piece
            }
        }

        // ---------- game actions ----------

        void newGame() {
            state = State.initial();
            history.clear();
            clearSelection();
            refresh();
        }

        void undo() {
            if (!history.isEmpty()) {
                state = history.pop();
                clearSelection();
                refresh();
            }
        }

        void flip() {
            flipped = !flipped;
            repaint();
        }

        private void clearSelection() {
            selR = -1;
            selC = -1;
            selMoves = new ArrayList<>();
        }

        private void refresh() {
            legal = legalMoves(state);
            boolean check = inCheck(state, state.whiteTurn);
            String side = state.whiteTurn ? "White" : "Black";
            gameOver = false;
            if (legal.isEmpty()) {
                gameOver = true;
                if (check) {
                    statusLabel.setText("Checkmate! " + (state.whiteTurn ? "Black" : "White") + " wins.");
                } else {
                    statusLabel.setText("Stalemate. The game is a draw.");
                }
            } else if (insufficientMaterial(state)) {
                gameOver = true;
                statusLabel.setText("Draw by insufficient material.");
            } else {
                statusLabel.setText(side + " to move" + (check ? " (check!)" : ""));
            }
            repaint();
        }

        private void play(Move m) {
            history.push(state);
            state = apply(state, m);
            clearSelection();
            refresh();
        }

        private Move askPromotion(List<Move> options) {
            String[] names = {"Queen", "Rook", "Bishop", "Knight"};
            char[] codes = {'q', 'r', 'b', 'n'};
            int choice = JOptionPane.showOptionDialog(this, "Promote pawn to:", "Promotion",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
            if (choice < 0) choice = 0;
            for (Move m : options) {
                if (Character.toLowerCase(m.promo) == codes[choice]) return m;
            }
            return options.get(0);
        }

        private void onClick(int x, int y) {
            if (gameOver) return;
            int sc = Math.floorDiv(x - bx, sq);
            int sr = Math.floorDiv(y - by, sq);
            if (sc < 0 || sc > 7 || sr < 0 || sr > 7) return;
            int r = flipped ? 7 - sr : sr;
            int c = flipped ? 7 - sc : sc;

            if (selR != -1) {
                List<Move> matches = new ArrayList<>();
                for (Move m : selMoves) {
                    if (m.tr == r && m.tc == c) matches.add(m);
                }
                if (!matches.isEmpty()) {
                    play(matches.size() > 1 ? askPromotion(matches) : matches.get(0));
                    return;
                }
            }

            char p = state.b[r][c];
            if (p != EMPTY && isWhite(p) == state.whiteTurn && !(r == selR && c == selC)) {
                selR = r;
                selC = c;
                selMoves = new ArrayList<>();
                for (Move m : legal) {
                    if (m.fr == r && m.fc == c) selMoves.add(m);
                }
            } else {
                clearSelection();
            }
            repaint();
        }

        // ---------- painting ----------

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int margin = 24;
            sq = Math.max(1, (Math.min(getWidth(), getHeight()) - 2 * margin) / 8);
            bx = (getWidth() - sq * 8) / 2;
            by = (getHeight() - sq * 8) / 2;

            boolean check = inCheck(state, state.whiteTurn);
            int[] kingPos = check ? findKing(state.b, state.whiteTurn) : null;

            for (int sr = 0; sr < 8; sr++) {
                for (int sc = 0; sc < 8; sc++) {
                    int r = flipped ? 7 - sr : sr;
                    int c = flipped ? 7 - sc : sc;
                    int x = bx + sc * sq, y = by + sr * sq;

                    g.setColor((r + c) % 2 == 0 ? LIGHT : DARK);
                    g.fillRect(x, y, sq, sq);

                    Move last = state.last;
                    if (last != null && ((r == last.fr && c == last.fc) || (r == last.tr && c == last.tc))) {
                        g.setColor(LAST);
                        g.fillRect(x, y, sq, sq);
                    }
                    if (r == selR && c == selC) {
                        g.setColor(SELECT);
                        g.fillRect(x, y, sq, sq);
                    }
                    if (kingPos != null && r == kingPos[0] && c == kingPos[1]) {
                        g.setPaint(new RadialGradientPaint(x + sq / 2f, y + sq / 2f, sq * 0.7f,
                                new float[]{0f, 1f},
                                new Color[]{new Color(230, 40, 40, 200), new Color(230, 40, 40, 0)}));
                        g.fillRect(x, y, sq, sq);
                    }

                    char p = state.b[r][c];
                    if (p != EMPTY) drawPiece(g, p, x, y);
                }
            }

            // Legal move hints
            for (Move m : selMoves) {
                int sr = flipped ? 7 - m.tr : m.tr;
                int sc = flipped ? 7 - m.tc : m.tc;
                int x = bx + sc * sq, y = by + sr * sq;
                boolean capture = state.b[m.tr][m.tc] != EMPTY || m.enPassant;
                g.setColor(new Color(20, 120, 40, 150));
                if (capture) {
                    g.setStroke(new BasicStroke(Math.max(3f, sq / 12f)));
                    int pad = sq / 10;
                    g.drawOval(x + pad, y + pad, sq - 2 * pad, sq - 2 * pad);
                } else {
                    int d = sq / 3;
                    g.fillOval(x + (sq - d) / 2, y + (sq - d) / 2, d, d);
                }
            }

            // Coordinates
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, sq / 6)));
            g.setColor(new Color(200, 200, 200));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 8; i++) {
                int file = flipped ? 7 - i : i;
                String f = String.valueOf((char) ('a' + file));
                g.drawString(f, bx + i * sq + (sq - fm.stringWidth(f)) / 2, by + 8 * sq + fm.getAscent() + 3);
                int rank = flipped ? i + 1 : 8 - i;
                String rk = String.valueOf(rank);
                g.drawString(rk, bx - fm.stringWidth(rk) - 6, by + i * sq + (sq + fm.getAscent()) / 2 - 2);
            }
            g.dispose();
        }

        private void drawPiece(Graphics2D g, char p, int x, int y) {
            Image img = images.get(p);
            if (img != null) {
                int pad = sq / 12;
                g.drawImage(img, x + pad, y + pad, sq - 2 * pad, sq - 2 * pad, null);
                return;
            }
            String glyph = GLYPHS.get(Character.toLowerCase(p));
            g.setFont(new Font("Dialog", Font.PLAIN, (int) (sq * 0.78)));
            FontMetrics fm = g.getFontMetrics();
            int gx = x + (sq - fm.stringWidth(glyph)) / 2;
            int gy = y + (sq - fm.getHeight()) / 2 + fm.getAscent();
            if (isWhite(p)) {
                g.setColor(new Color(40, 40, 40));
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        g.drawString(glyph, gx + dx, gy + dy);
                    }
                }
                g.setColor(Color.WHITE);
            } else {
                g.setColor(new Color(25, 25, 25));
            }
            g.drawString(glyph, gx, gy);
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> {
            final JFrame frame = new JFrame("Chess");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            final JLabel status = new JLabel(" ", SwingConstants.CENTER);
            status.setFont(new Font("SansSerif", Font.BOLD, 18));
            status.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            final BoardPanel board = new BoardPanel(status);

            JButton newGame = new JButton("New Game");
            JButton undo = new JButton("Undo");
            JButton flip = new JButton("Flip Board");
            newGame.addActionListener(e -> board.newGame());
            undo.addActionListener(e -> board.undo());
            flip.addActionListener(e -> board.flip());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            buttons.add(newGame);
            buttons.add(undo);
            buttons.add(flip);

            frame.add(status, BorderLayout.NORTH);
            frame.add(board, BorderLayout.CENTER);
            frame.add(buttons, BorderLayout.SOUTH);

            frame.setSize(760, 860);
            frame.setMinimumSize(new Dimension(440, 520));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}