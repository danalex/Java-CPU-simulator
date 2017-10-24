package cpusimulator.memory;

/**
 * Data memory model. Each cell has 16 bits of data and can be readed or written.
 * This memory bus have no capabilities to protect memory data from corruption.
 * 
 * @author Daniel Alexandre 2011
 */
public class DataMemory16 implements Memory16 {
    
    /** Each memory cell is emulated as java array cell of short (16bit) type. */
    private short[] memoryCells;
    /** This handler called every time when CPU attempts to read or write something out of memory range. */
    private Runnable exceptionHandler;
    
    /**
     * Creates new 16-bit data memory model with invalid address handler.
     * @param length count of memory cells
     * @param invalidAddressHandler callable object for handling incorrect uses
     * of this memory model
     */
    public DataMemory16(int length, Runnable invalidAddressHandler) {
        if(length > 256) throw new IllegalArgumentException();
        if(invalidAddressHandler == null) throw new NullPointerException();
        memoryCells = new short[length];
        exceptionHandler = invalidAddressHandler;
    }
    
    /**
     * Returns memory cells count.
     * @return count of memory cells in this data memory model
     */
    public int getLength() {
        return memoryCells.length;
    }

    @Override
    public short read(byte address) {
        try {
            return memoryCells[address & 0xff];
        } catch(ArrayIndexOutOfBoundsException exception) {
            exceptionHandler.run();
            return 0;
        }
    }

    @Override
    public void write(byte address, short value) {
        try {
            memoryCells[address & 0xff] = value;
        } catch(ArrayIndexOutOfBoundsException exception) {
            exceptionHandler.run();
        }
    }
}
