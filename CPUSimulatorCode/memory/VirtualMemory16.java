package cpusimulator.memory;

import cpusimulator.Device;
import java.util.ArrayList;

/**
 * This is very useful thing designed to compose many devices into one bus
 * system.
 * 
 * @author Daniel Alexandre 2011
 */
public class VirtualMemory16 implements Memory16 {
    
    /** Just a data class used to keep information about memory bus. */
    private static class MemoryMapRecord {
        
        /** Mapped memory. */
        public Memory16 mappedMemory;
        /** Offset in virtual memory space. */
        public int mappingOffset;
        /** Offset in device memory space. */
        public int memoryOffset;
    }
    
    /** List of mapped devices; max count of mapped devices is 255. */
    public ArrayList<MemoryMapRecord> mapped = new ArrayList<MemoryMapRecord>();
    /** Information about device map ranges. */
    public byte[] mapIndices;
    /** Callable object which will be called each time when CPU will try to read from unmapped memory cell */
    public Runnable unmappedExceptionHandler;
    /** Callable object to handle events with incorrect address usage in mapped memory devices. */
    public Runnable addressExceptionHandler;
    
    /**
     * Creates new virtual memory card with specified length and bad event handlers.
     * @param length size of memory, max size is 256
     * @param operateOnUnmappedHandler callable object to handle attempts to read from and write to unmapped memory cells
     * @param invalidAddressHandler callable object to handle operations with some invalid address specified
     * @throws NullPointerException if one of handlers is null
     * @throws IllegalArgumentException during attempt to create virtual memory with more than 256 memory cells
     */
    public VirtualMemory16(int length, Runnable operateOnUnmappedHandler, Runnable invalidAddressHandler) {
        if(length > 256) throw new IllegalArgumentException();
        if(operateOnUnmappedHandler == null) throw new NullPointerException();
        if(invalidAddressHandler == null) throw new NullPointerException();
        mapIndices = new byte[length];
        unmappedExceptionHandler = operateOnUnmappedHandler;
        addressExceptionHandler = invalidAddressHandler;
        mapped.add(null); // device with #0 mapping index means "no device mapped"
    }
    
    /**
     * Attaches some device to specified virtual memory cells range.
     * @param device device which will be attached
     * @param memoryAddress device memory offset
     * @param mapAddress mapping offset
     * @throws IllegalArgumentException if there is no more devices slots available
     * or if memory addresses points out of the virtual memory range or during
     * attempt to attach device to memory range with already attached cells
     */
    public void map(Device device, int memoryAddress, int mapAddress) {
        // check for free device space availability
        if(mapped.size() > 256) throw new IllegalStateException("too much mapped memories");
        // check for memory ranges
        if(memoryAddress < 0 || device.getMappingLength() < 0 || mapAddress + device.getMappingLength() > mapIndices.length)
            throw new IllegalArgumentException();
        // check for memory conflicts
        int mapLength = device.getMappingLength();
        for(int i = 0; i < mapLength; i ++)
            if(mapIndices[mapAddress + i] != 0)
                throw new IllegalStateException("it conflicts with other mapped memory");
        int mapIndex = mapped.size();
        // creating device info record
        MemoryMapRecord record = new MemoryMapRecord();
        record.mappedMemory = device.getMappingMemory();
        record.mappingOffset = mapAddress;
        record.memoryOffset = memoryAddress;
        mapped.add(record); // add it to our device list
        // perform an attaching to cells
        for(int i = 0; i < mapLength; i ++)
            mapIndices[mapAddress + i] = (byte)mapIndex;
    }

    @Override
    public short read(byte address) {
        try {
            // get mapped device
            int device = mapIndices[address & 0xff] & 0xff;
            if(device == 0) {
                // memory cell is not mapped yet
                unmappedExceptionHandler.run();
                return 0;
            }
            // memory cell have attached device. calculate it's address and redirect read operation to attached device
            MemoryMapRecord record = mapped.get(device & 0xff);
            return record.mappedMemory.read((byte)((address & 0xff) - record.mappingOffset + record.memoryOffset));
        } catch(ArrayIndexOutOfBoundsException exception) {
            addressExceptionHandler.run();
            return 0;
        }
    }

    @Override
    public void write(byte address, short value) {
        try {
            // get mapped device
            int device = mapIndices[address & 0xff] & 0xff;
            if(device == 0) unmappedExceptionHandler.run(); // memory cell is not mapped yet
            else {
                // memory cell have attached device. calculate it's address and redirect write operation to attached device
                MemoryMapRecord record = mapped.get(device & 0xff);
                record.mappedMemory.write((byte)((address & 0xff) - record.mappingOffset + record.memoryOffset), value);
            }
        } catch(ArrayIndexOutOfBoundsException exception) {
            addressExceptionHandler.run();
        }
    }
}
