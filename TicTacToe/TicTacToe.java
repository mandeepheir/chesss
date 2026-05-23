import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class TicTacToe extends JFrame implements KeyListener {

    private JButton[][] buttons = new JButton[3][3];

    private int row = 0;
    private int col = 0;

    private char player = 'X';
    private boolean gameOver = false;

    public TicTacToe() {

        setTitle("Tic Tac Toe");
        setSize(400, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridLayout(3, 3));

        // Create grid
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton("");
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 60));
                buttons[i][j].setFocusPainted(false);
                buttons[i][j].setEnabled(false); // keyboard-only game
                add(buttons[i][j]);
            }
        }

        setFocusable(true);
        addKeyListener(this);

        highlight();
        setLocationRelativeTo(null);
    }

    @Override
    public void keyPressed(KeyEvent e) {

        if (gameOver && e.getKeyCode() != KeyEvent.VK_R) return;

        unhighlight();

        switch (e.getKeyCode()) {

            case KeyEvent.VK_UP -> row = (row > 0) ? row - 1 : 2;
            case KeyEvent.VK_DOWN -> row = (row < 2) ? row + 1 : 0;
            case KeyEvent.VK_LEFT -> col = (col > 0) ? col - 1 : 2;
            case KeyEvent.VK_RIGHT -> col = (col < 2) ? col + 1 : 0;

            case KeyEvent.VK_ENTER -> makeMove();

            case KeyEvent.VK_R -> restart();
        }

        highlight();
    }

    private void makeMove() {

        if (!buttons[row][col].getText().equals("") || gameOver) return;

        buttons[row][col].setText(String.valueOf(player));

        if (checkWin(player)) {
            gameOver = true;
            JOptionPane.showMessageDialog(this, player + " Wins! Press R to restart");
            return;
        }

        if (isFull()) {
            gameOver = true;
            JOptionPane.showMessageDialog(this, "Draw! Press R to restart");
            return;
        }

        player = (player == 'X') ? 'O' : 'X';
    }

    private boolean checkWin(char p) {

        String s = String.valueOf(p);

        for (int i = 0; i < 3; i++) {

            if (buttons[i][0].getText().equals(s) &&
                buttons[i][1].getText().equals(s) &&
                buttons[i][2].getText().equals(s)) {

                winHighlight(buttons[i][0], buttons[i][1], buttons[i][2]);
                return true;
            }

            if (buttons[0][i].getText().equals(s) &&
                buttons[1][i].getText().equals(s) &&
                buttons[2][i].getText().equals(s)) {

                winHighlight(buttons[0][i], buttons[1][i], buttons[2][i]);
                return true;
            }
        }

        if (buttons[0][0].getText().equals(s) &&
            buttons[1][1].getText().equals(s) &&
            buttons[2][2].getText().equals(s)) {

            winHighlight(buttons[0][0], buttons[1][1], buttons[2][2]);
            return true;
        }

        if (buttons[0][2].getText().equals(s) &&
            buttons[1][1].getText().equals(s) &&
            buttons[2][0].getText().equals(s)) {

            winHighlight(buttons[0][2], buttons[1][1], buttons[2][0]);
            return true;
        }

        return false;
    }

    private boolean isFull() {

        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                if (buttons[i][j].getText().equals(""))
                    return false;

        return true;
    }

    private void restart() {

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setText("");
                buttons[i][j].setBackground(null);
                buttons[i][j].setBorder(null);
            }
        }

        row = 0;
        col = 0;
        player = 'X';
        gameOver = false;

        highlight();
    }

    private void highlight() {
        buttons[row][col].setBorder(BorderFactory.createLineBorder(Color.RED, 3));
    }

    private void unhighlight() {
        buttons[row][col].setBorder(null);
    }

    private void winHighlight(JButton a, JButton b, JButton c) {
        a.setBackground(Color.GREEN);
        b.setBackground(Color.GREEN);
        c.setBackground(Color.GREEN);
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TicTacToe().setVisible(true);
        });
    }
}