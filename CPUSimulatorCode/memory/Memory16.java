package cpusimulator.memory;

/**
 * Abstract 16-bit memory model, designed to read and write data. Max size is
 * limited by pointer size (8 bit), so we can have memory with max of 256 bits.
 * 
 * @author Daniel Alexandre 2011
 */
public interface Memory16 {
    
    /**
     * Reads data from memory model by given address.
     * @param address memory pointer
     * @return readed data
     */
    public short read(byte address);
    
    /**
     * Writes data to memory model by given address.
     * @param address memory pointer
     * @param value data which we have to write
     */
    public void write(byte address, short value);
}
