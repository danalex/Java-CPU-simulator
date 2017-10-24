package cpusimulator;

import cpusimulator.memory.Memory16;

/**
 * This class represents device. Device has it own memory through which I/O
 * operations between CPU and device is performed.
 * 
 * @author Daniel Alexandre 2011
 */
public interface Device {
    
    /**
     * Returns count of memory cells through which I/O operations should be
     * performed.
     * @return count of memory cells
     */
    public int getMappingLength();
    
    /**
     * Returns device's memory. Can be readable and/or writable according to
     * device specification.
     * @return 16-bit memory
     */
    public Memory16 getMappingMemory();
}
