import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.SQLOutput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine {
    static int maxDepth = 30;
    static long[] pvLength = new long[maxDepth];
    static long[][] pvTable = new long[maxDepth][maxDepth];
    static long[][] previousPV = new long[maxDepth][maxDepth];

    static int totalDepth;
    static int nodes;
    static long thinkTime, startTime, endTime;
    static int timeOut = 123456789;

    private static PolyglotOpeningBook openingBook;

    public static boolean loadOpeningBook(String bookPath) throws IOException {
        openingBook = new PolyglotOpeningBook(bookPath);
        try {
            openingBook.load();
            return true;
        } catch (IOException e) {
            // Handle the exception, maybe log it
            e.printStackTrace();
            return false;
        }
    }

    private static int getBookMove(Board board){
        System.out.println("Trying to get book move");
        System.out.println(openingBook);
        if(openingBook == null){
            System.out.println("Opening book not loaded");
            return 0;
        }
        long zobristKey = board.zobristKey;
        System.out.println("Book move zobrist key: " + zobristKey);
        System.out.println("Book move is: " + openingBook.getMove(zobristKey));
        return openingBook.getMove(zobristKey);
    }

    public static Board engineMove(Board board, int timeLimit) {
        Board bestBoard = null, temp;

        startTime = System.currentTimeMillis();
        thinkTime = timeLimit;

        int bookMove = getBookMove(board);
        if(bookMove != 0){
            System.out.println("Using opening book move: " +MoveList.toStringMove(bookMove));
            board.makeMove(bookMove);
            return board;
        }

        for (int i = 1; i < maxDepth; i++) { //arbitrary depth limit
            if (i == 1) { //so first move can be made
                thinkTime = Integer.MAX_VALUE;
            } else {
                thinkTime = timeLimit;
            }

            totalDepth = i;
            nodes = 0;

            temp = negaMax(board, i);
            if (temp != null) {
                bestBoard = temp;
                previousPV = pvTable.clone();
                pvTable = new long[maxDepth][maxDepth];

                endTime = System.currentTimeMillis();
            } else {
                break;
            }
            if (MoveGeneration.getMoves(bestBoard).count == 0 && (bestBoard.fKing & bestBoard.eAttackMask) != 0) {
                try {
                    Thread.sleep(timeLimit - (System.currentTimeMillis() - startTime));
                } catch (InterruptedException ignored) {
                }
                break;
            }
        }
        return bestBoard;
    }

    public static Board engineMove(int depth, Board board) {
        //TODO: fix opening book
        /*int bookMove = getBookMove(board);
        if(bookMove != 0){
            System.out.println("Using opening book move: " +MoveList.toStringMove(bookMove));
            board.makeMove(bookMove);
            return board;
        }*/

        Board bestBoard = null, temp;
        thinkTime = Integer.MAX_VALUE;
        startTime = System.currentTimeMillis();

        for (int i = 1; i <= depth; i++) {

            totalDepth = i;
            nodes = 0;

            temp = negaMax(board, i);
            if (temp != null) {
                bestBoard = temp;
                previousPV = pvTable.clone();
                pvTable = new long[maxDepth][maxDepth];

                endTime = System.currentTimeMillis();
                System.out.printf("Depth: %-2d Time: %-11s Nodes: %,-11d PV: %s\n", i, (endTime - startTime + "ms"), nodes, MoveList.toStringPv(previousPV));
            } else {
                break;
            }
            if (MoveGeneration.getMoves(bestBoard).count == 0 && (bestBoard.fKing & bestBoard.eAttackMask) != 0) {
                break;
            }
        }
        System.out.println();
        return bestBoard;
    }
    //Wrapper method for negaMax
    private static Board negaMax(Board board, int depth) {
        //Refresh repetition table
        Repetition.refreshTables();

        //initialize alpha and beta values
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        //Flag indicating the type of move stored in the transposition table
        int hashFlag = TTable.flagAlpha; //if pv move not found flag this node as alpha
        //Initialize principial variation length
        pvLength[0] = 0;

        //Get all legal moves for the current board position
        MoveList moveList = MoveGeneration.getMoves(board);
        //Reorder moves based on the principal variation
        moveList.reorder(board, previousPV[0][0]);
        //variables to store best board and evaluation
        Board bestBoard = null;
        Board nextBoard;
        double eval;
        boolean foundPV = false;
        boolean repetition, alphaIsARepetition = false;
        //Iterate through all legal moves
        for (int i = 0; i < moveList.count; i++) {
            //Make a copy of the board and apply the current move
            nextBoard = new Board(board);
            nextBoard.makeMove(moveList.moves[i]);
            //Check for repetition
            repetition = Repetition.addToHistory(nextBoard.zobristKey, Repetition.treeFlag);
            //Evaluate position after the move
            if (repetition) {
                eval = 0;
            } else {
                //If PV is found, perform full search on the move
                if (foundPV) {
                    eval = -negaMax(nextBoard, depth - 1, -alpha - 1, -alpha);
                    if ((eval > alpha) && (eval < beta)) { //if move searched after pv found is better than pv then have to full search move
                        eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
                    }
                } else {
                    //Otherwise, perform normal search
                    eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
                }
            }
            Repetition.removeFromHistory(nextBoard.zobristKey);

            //System.out.printf("Move: %s, Eval: %.2f\n",MoveList.toStringMove(moveList.moves[i]), eval);
            //Check if time limit is reached
            if (System.currentTimeMillis() - startTime > thinkTime) { //if timelimit reached
                return null;
            }
            // Update alpha and store PV
            if (eval > alpha) {
                alpha = eval;
                alphaIsARepetition = repetition;
                bestBoard = nextBoard;
                foundPV = true;
                hashFlag = TTable.flagExact;

                //Store the PV
                pvTable[0][0] = moveList.moves[i];
                for (int j = 1; j < pvLength[1]; j++) {
                    pvTable[0][j] = pvTable[1][j];
                }
                pvLength[0] = pvLength[1];
            }
        }
        //Store the result in the transposition table if it's not a repetition
        if (!alphaIsARepetition) {
            TTable.writeValue(board.zobristKey, depth, alpha, hashFlag);
        }
        return bestBoard;
    }
    //main search functiono
    private static double negaMax(Board board, int depth, double alpha, double beta) {
        //calculate pv index
        int pvIndex = totalDepth - depth;
        pvLength[pvIndex] = pvIndex;
        //Initialize hash flag
        int hashFlag = TTable.flagAlpha;
        //retrieve the cached evaluation if available
        double eval = TTable.getValue(board.zobristKey, depth, alpha, beta);
        if (eval != TTable.noValue) { //if this position is already evaluated with this depth
            nodes++;
            return eval;
        }
        //Base case: if maximum depth is reached, evaluate the position
        if (depth == 0) {
            nodes++;
            int mate = MoveGeneration.mateCheck(board);
            if (mate == 0) {
                //Apply quiescence search
                eval = quiescence(board, alpha, beta);
                return eval;
            } else if (mate == 1) {
                //checkmate
                return -999999999 - depth;
            } else {
                //stalemate
                return 0;
            }
        }
        //Generate legal moves for the current position
        MoveList moveList = MoveGeneration.getMoves(board);
        if (moveList.count == 0) {
            // Check for checkmate or stalemate
            nodes++;
            if ((board.fKing & board.eAttackMask) != 0) {
                return -999999999 - depth;
            } else {
                return 0;
            }
        }
        //Create a copy of the board
        Board nextBoard;
        boolean foundPV = false;
        boolean repetition, alphaIsARepetition = false;
        //Reorder moves based on the principal variation and Iterate through all legal moves
        moveList.reorder(board, previousPV[pvIndex][pvIndex]);
        for (int i = 0; i < moveList.count; i++) {
            if (System.currentTimeMillis() - startTime > thinkTime) { //if time limit reached
                return timeOut;
            }
            //Make a copy of the board and apply the move
            nextBoard = new Board(board);
            nextBoard.makeMove(moveList.moves[i]);
            //Check for repetition
            repetition = Repetition.addToHistory(nextBoard.zobristKey, Repetition.treeFlag);
            //Evaluate the position after the move
            if (repetition) {
                eval = 0;
            } else {
                if (foundPV) {
                    //If PV is found, perform full search on the move
                    eval = -negaMax(nextBoard, depth - 1, -alpha - 1, -alpha);
                    if ((eval > alpha) && (eval < beta)) { //if move searched after pv found is better than pv then have to full search move
                        eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
                    }
                } else {
                    //Otherwise, perform normal search
                    eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
                }
            }
            Repetition.removeFromHistory(nextBoard.zobristKey); //once all searches completed with added repetition count, can remove the count

            if (System.currentTimeMillis() - startTime > thinkTime) { //if time limit reached
                return timeOut;
            }
            //Update alpha and store PV
            if (eval >= beta) {
                if (!repetition) {
                    TTable.writeValue(board.zobristKey, depth, beta, TTable.flagBeta);
                } //if repetition occurs, this value is not trustworthy

                return beta;
            }
            if (eval > alpha) {
                hashFlag = TTable.flagExact;
                alpha = eval;
                foundPV = true;
                alphaIsARepetition = repetition; //if eval is 0 because of repetition and alpha < 0, alpha cannot be trusted
                pvTable[pvIndex][pvIndex] = moveList.moves[i];
                for (int j = pvIndex+1; j < pvLength[pvIndex+1]; j++) {
                    pvTable[pvIndex][j] = pvTable[pvIndex+1][j];
                }
                pvLength[pvIndex] = pvLength[pvIndex+1];
            }
        }
        //Store the result in the transposition table if it's not a repetition
        if (!alphaIsARepetition) {
            TTable.writeValue(board.zobristKey, depth, alpha, hashFlag);
        }
        return alpha;
    }

    private static double quiescence(Board board, double alpha, double beta) {
        double eval = Evaluation.evaluation(board);
        if (eval >= beta) {
            return beta;
        }
        if (alpha < eval) {
            alpha = eval;
        }

        MoveList moveList = MoveGeneration.getMoves(board);
        Board nextBoard;
        moveList.reorder(board, 0);

        for (int i = 0; i < moveList.count; i++) {
            if (MoveList.getCaptureFlag(moveList.moves[i]) == 1) {
                nextBoard = new Board(board);
                nextBoard.makeMove(moveList.moves[i]);
                eval = -quiescence(nextBoard, -beta, -alpha);

                if (eval >= beta) {
                    return beta;
                }
                if (eval > alpha) {
                    alpha = eval;
                }
            }
        }
        return alpha;
    }

    public static SearchNode getSearchNodes(Board board, int depth) {
        SearchNode parentNode = null;
        thinkTime = Integer.MAX_VALUE;
        startTime = System.currentTimeMillis();

        for (int i = 1; i <= depth; i++) {
            parentNode = new SearchNode(board);

            totalDepth = i;
            nodes = 0;

            negaMax(board, i, parentNode);
            previousPV = pvTable.clone();
            pvTable = new long[maxDepth][maxDepth];

            endTime = System.currentTimeMillis();
            System.out.printf("Depth: %-2d Time: %-11s Nodes: %,-11d PV: %s\n", i, (endTime - startTime + "ms"), nodes, MoveList.toStringPv(previousPV));
        }
        System.out.println();
        return parentNode;
    }

    private static void negaMax(Board board, int depth, SearchNode parentNode) {
        parentNode.eval = negaMax(board, depth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, parentNode);
    }

    private static double negaMax(Board board, int depth, double alpha, double beta, SearchNode parentNode) {
        int pvIndex = totalDepth - depth;
        pvLength[pvIndex] = pvIndex;

        int hashFlag = TTable.flagAlpha;
        double eval = TTable.getValue(board.zobristKey, depth, alpha, beta);

        if (depth == 0) {
            nodes++;
            int mate = MoveGeneration.mateCheck(board);
            if (mate == 0) {
                eval = quiescence(board, alpha, beta, parentNode);
                //TTable.writeValue(board.zobristKey, depth, eval, TTable.flagExact); //this is breaking something
                return eval;
            } else if (mate == 1) {
                //TTable.writeValue(board.zobristKey, depth, -999999999 - depth, TTable.flagExact);
                return -999999999 - depth;
            } else {
                //TTable.writeValue(board.zobristKey, depth, 0, TTable.flagExact);
                return 0;
            }
        }

        MoveList moveList = MoveGeneration.getMoves(board);
        if (moveList.count == 0) {
            nodes++;
            if ((board.fKing & board.eAttackMask) != 0) {
                return -999999999 - depth;
            } else {
                return 0;
            }
        }

        Board nextBoard;
        SearchNode currentNode;
        //boolean foundPV = false;
        boolean repetition, alphaIsARepetition = false;

        moveList.reorder(board, previousPV[pvIndex][pvIndex]);
        for (int i = 0; i < moveList.count; i++) {
            if (System.currentTimeMillis() - startTime > thinkTime) { //if time limit reached
                return timeOut;
            }

            nextBoard = new Board(board);
            nextBoard.makeMove(moveList.moves[i]);
            currentNode = new SearchNode(nextBoard);


            /*if (foundPV) {
                eval = -negaMax(nextBoard, depth - 1, -alpha - 1, -alpha);
                if ((eval > alpha) && (eval < beta)) { //if move searched after pv found is better than pv then have to full search move
                    eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
                }
            } else {
                eval = -negaMax(nextBoard, depth - 1, -beta, -alpha);
            }*/
            repetition = Repetition.addToHistory(nextBoard.zobristKey, Repetition.treeFlag);
            if (repetition) {
                eval = 0;
            } else {
                eval = -negaMax(nextBoard, depth - 1, -beta, -alpha, currentNode);
            }
            Repetition.removeFromHistory(nextBoard.zobristKey); //once all searches completed with added repetition count, can remove the count
            currentNode.eval = eval;

            if (System.currentTimeMillis() - startTime > thinkTime) { //if time limit reached
                return timeOut;
            }

            if (eval >= beta) {
                if (!repetition) {
                    TTable.writeValue(board.zobristKey, depth, beta, TTable.flagBeta);
                } //if repetition occurs, this value is not trustworthy
                currentNode.flag = SearchNode.AboveBeta;
                parentNode.addChild(currentNode);
                return beta;
            }
            if (eval > alpha) {
                hashFlag = TTable.flagExact;
                alpha = eval;
                //foundPV = true;
                alphaIsARepetition = repetition; //if eval is 0 because of repetition and alpha < 0, alpha cannot be trusted

                pvTable[pvIndex][pvIndex] = moveList.moves[i];
                for (int j = pvIndex+1; j < pvLength[pvIndex+1]; j++) {
                    pvTable[pvIndex][j] = pvTable[pvIndex+1][j];
                }
                pvLength[pvIndex] = pvLength[pvIndex+1];

                currentNode.flag = SearchNode.NewAlpha;
                parentNode.addChild(currentNode);
            } else {
                currentNode.flag = SearchNode.BelowAlpha;
                parentNode.addChild(currentNode);
            }
        }
        if (!alphaIsARepetition) {
            TTable.writeValue(board.zobristKey, depth, alpha, hashFlag);
        }
        return alpha;
    }

    private static double quiescence(Board board, double alpha, double beta, SearchNode parentNode) {
        double eval = Evaluation.evaluation(board);
        if (eval >= beta) {
            return beta;
        }
        if (alpha < eval) {
            alpha = eval;
        }

        MoveList moveList = MoveGeneration.getMoves(board);
        Board nextBoard;
        SearchNode currentNode;
        moveList.reorder(board, 0);

        for (int i = 0; i < moveList.count; i++) {
            if (MoveList.getCaptureFlag(moveList.moves[i]) == 1) {
                nextBoard = new Board(board);
                nextBoard.makeMove(moveList.moves[i]);
                currentNode = new SearchNode(nextBoard);

                eval = -quiescence(nextBoard, -beta, -alpha, currentNode);
                currentNode.eval = eval;

                if (eval >= beta) {
                    currentNode.flag = SearchNode.AboveBeta;
                    parentNode.addChild(currentNode);
                    return beta;
                }
                if (eval > alpha) {
                    currentNode.flag = SearchNode.NewAlpha;
                    parentNode.addChild(currentNode);
                    alpha = eval;
                } else {
                    currentNode.flag = SearchNode.BelowAlpha;
                    parentNode.addChild(currentNode);
                }
            }
        }
        return alpha;
    }
}
