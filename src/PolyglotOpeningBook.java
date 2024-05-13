import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PolyglotOpeningBook {
    private static final int HEADER_SIZE = 40;
    private static final int ENTRY_SIZE = 16;

    private final String bookPath;
    private long[] zobristKeys;
    private int[] moves;
    private int[] weights;
    private int[] learn;

    public PolyglotOpeningBook(String bookPath) {
        this.bookPath = bookPath;
    }

    public void load() throws IOException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(bookPath))) {
            dis.skipBytes(HEADER_SIZE); // Skip header

            List<Long> zobristKeysList = new ArrayList<>();
            List<Integer> movesList = new ArrayList<>();
            List<Integer> weightsList = new ArrayList<>();
            List<Integer> learnList = new ArrayList<>();

            while (dis.available() >= ENTRY_SIZE) {
                zobristKeysList.add(dis.readLong());
                movesList.add(dis.readInt());
                weightsList.add(dis.readUnsignedShort());
                learnList.add(dis.readUnsignedShort());
            }

            // Convert lists to arrays
            zobristKeys = zobristKeysList.stream().mapToLong(Long::valueOf).toArray();
            moves = movesList.stream().mapToInt(Integer::valueOf).toArray();
            weights = weightsList.stream().mapToInt(Integer::valueOf).toArray();
            learn = learnList.stream().mapToInt(Integer::valueOf).toArray();
        }
    }

    public int getMoveIndex(long zobristKey) {
        for (int i = 0; i < zobristKeys.length; i++) {
            if (zobristKeys[i] == zobristKey) {
                return i;
            }
        }
        return -1; // Not found
    }

    public int getMove(long zobristKey) {
        int index = getMoveIndex(zobristKey);
        return index != -1 ? moves[index] : 0; // Return 0 if move not found
    }
}
