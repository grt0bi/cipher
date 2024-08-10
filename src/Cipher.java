import java.io.IOException;

public class Cipher {
    static boolean player = true;
    static boolean boardFlipped = false;

    public static void main(String[] args) throws IOException {
        init();
        Board board = new Board(PosConstants.startPos);

        Gui gui = new Gui(board, 1.2, boardFlipped);
        //TODO: fix
        /*String openingBookPath = "Images/komodo.bin";
        if (Engine.loadOpeningBook(openingBookPath)) {
            System.out.println("Opening book loaded successfully.");
            Engine.engineMove(6, board);
        } else {
            System.out.println("Failed to load opening book. Proceeding without it.");
        }*/
        Engine.engineMove(6, board);
        play(board, gui, 3000, player);

        Client client = new Client(1409);

        client.initMatch();
    }

    static void play(Board board, Gui gui, int timeLimit, boolean player) {
        while (MoveGeneration.getMoves(board).count > 0 && Repetition.getRepetitionAmount(board.zobristKey, Repetition.historyFlag) < 3) {
            System.out.println(board.boardToFen());
            if (board.player == player) {
                board = PlayerGame.playerMove(board, gui);
                System.out.println();

            } else {
                board = Engine.engineMove(board, timeLimit);
            }

            Repetition.addToHistory(board.zobristKey, Repetition.historyFlag);

            gui.panel.board = board;
            gui.repaint();
        }
        if (board.player) {
            System.out.printf("%.2f\n", Evaluation.evaluation(board)/100);
        } else {
            System.out.printf("%.2f\n", -Evaluation.evaluation(board)/100);
        }
        if (Repetition.getRepetitionAmount(board.zobristKey, Repetition.historyFlag) >= 3) {
            System.out.println("Draw by repetition!");
        } else {
            if ((board.fKing & board.eAttackMask) != 0) {
                System.out.println("Checkmate!");
            } else{
                System.out.println("Stalemate!");
            }
        }
    }
    static void init() {
        MoveGeneration.initAttack();
        Zobrist.initKeys();
    }
}
