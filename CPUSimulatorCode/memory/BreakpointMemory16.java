package cpusimulator.memory;

import cpusimulator.Breakpoint16;

/**
 * Memory used to handle read and writes at some addresses.
 * Normally used for breakpoints.
 * 
 * @author Daniel Alexandre 2011
 */
public class BreakpointMemory16 implements Memory16 {

    /**
     * A record with linked-list capabilities which contains all breakpoint-related info.
     */
    private class BreakpointHandlerRecord extends Breakpoint16 {
        
        /** Each breakpoint can be enabled or disabled, here is stored information about breakpoint state */
        public boolean enabled;
        /** Breakpoint handler, this object will be called when breakpoint catches operation */
        public Runnable handler;
        /** Breakpoint address */
        public byte address;
        /** Breakpoint type: it can be one of BREAKPOINT_READ, BREAKPOINT_WRITE, BREAKPOINT_CHANGE */
        public int type;
        
        /** Link to the next breakpoint. Used to hold many breakpoints for the same address */
        public BreakpointHandlerRecord next;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void remove() {
            if(breakpointIndices[address & 0xFF] == this) {
                breakpointIndices[address & 0xFF] = next;
            } else {
                BreakpointHandlerRecord record = breakpointIndices[address & 0xFF];
                while(record.next != this && record.next != null) record = record.next;
                if(record.next == this) record.next = next;
            }
        }

        @Override
        public void setHandler(Runnable runnable) {
            handler = runnable;
        }

        @Override
        public int getAddress() {
            return address & 0xFF;
        }

        @Override
        public int getType() {
            return type;
        }
        
        public void runHandler() {
            if(handler != null) handler.run();
        }

        @Override
        public short getValue() {
            return mem16.read(address);
        }

        @Override
        public Memory16 getMemory() {
            return mem16;
        }
    }
    
    /** Original memory. All reads and writes will be forwarded to this memory. */
    public Memory16 mem16;
    /** Breakpoint records for each cell of memory. */
    public BreakpointHandlerRecord[] breakpointIndices;
    /** This callable object will be called when illegal address operation will occurred. */
    public Runnable addressExceptionHandler;
    
    /**
     * Creates new breakpoint memory with specified destination memory, length and invalid address handler.
     * @param mem16 memory, to which read/write operations will be forwarded
     * @param length bytes count, must be in range 0..255 inclusive
     * @param invalidAddressHandler handler to handler operations with wrong addresses
     * @throws IllegalArgumentException if length is wrong
     * @throws NullPointerException if mem16 or invalidAddressHandler are equals to null
     */
    public BreakpointMemory16(Memory16 mem16, int length, Runnable invalidAddressHandler) {
        if(length > 256) throw new IllegalArgumentException();
        if(invalidAddressHandler == null) throw new NullPointerException();
        if(mem16 == null) throw new NullPointerException();
        this.mem16 = mem16;
        breakpointIndices = new BreakpointHandlerRecord[length];
        addressExceptionHandler = invalidAddressHandler;
    }
    
    /**
     * Creates a breakpoint with the specified type and the specified memory address
     * @param memoryAddress memory cell to which new breakpoint will be attached
     * @param type type of breakpoint, should be one of BREAKPOINT_READ, BREAKPOINT_WRITE or BREAKPOINT_CHANGE
     * @return newly created breakpoint
     * @throws IllegalArgumentException if type or memory address is illegal
     */
    public Breakpoint16 createBreakpoint(byte memoryAddress, int type) {
        if(memoryAddress < 0 || memoryAddress >= breakpointIndices.length) throw new IllegalArgumentException();
        if(type != Breakpoint16.BREAKPOINT_CHANGE && type != Breakpoint16.BREAKPOINT_READ && type != Breakpoint16.BREAKPOINT_WRITE) throw new IllegalArgumentException();
        BreakpointHandlerRecord record = new BreakpointHandlerRecord();
        record.next = breakpointIndices[memoryAddress & 0xFF];
        record.address = memoryAddress;
        record.type = type;
        breakpointIndices[memoryAddress & 0xFF] = record;
        return record;
    }

    @Override
    public short read(byte address) {
        try {
            BreakpointHandlerRecord record = breakpointIndices[address & 0xFF];
            while(record != null) {
                if(record.enabled) {
                    if(record.type == Breakpoint16.BREAKPOINT_READ) record.runHandler();
                }
                record = record.next;
            }
            return mem16.read(address);
        } catch(ArrayIndexOutOfBoundsException exception) {
            addressExceptionHandler.run();
            return 0;
        }
    }

    @Override
    public void write(byte address, short value) {
        try {
            BreakpointHandlerRecord record = breakpointIndices[address & 0xFF];
            while(record != null) {
                if(record.enabled) {
                    if(record.type == Breakpoint16.BREAKPOINT_WRITE) record.runHandler();
                    if(record.type == Breakpoint16.BREAKPOINT_CHANGE) {
                        int oldValue = mem16.read(address);
                        if(value != oldValue) record.runHandler();
                    }
                }
                record = record.next;
            }
            mem16.write(address, value);
        } catch(ArrayIndexOutOfBoundsException exception) {
            addressExceptionHandler.run();
        }
    }
}
