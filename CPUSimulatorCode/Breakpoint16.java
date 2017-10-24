package cpusimulator;

import cpusimulator.memory.Memory16;

/**
 * Breakpoint used to handle READ/WRITE operations during CPU simulation.
 * Main implementation for this class is BreakpointMemory16.
 * 
 * @author Daniel Alexandre 2011
 */
public abstract class Breakpoint16 {
    
    /** Breakpoint with this type will be activated by reading */
    public static final int BREAKPOINT_READ = 1;
    /** Breakpoint with this type will be activated by writing */
    public static final int BREAKPOINT_WRITE = 2;
    /** Breakpoint with this type will be activated by changing value */
    public static final int BREAKPOINT_CHANGE = 3;
    
    /**
     * Returns true if this breakpoint is enabled.
     * If breakpoint isn't enabled, it must behave like there is no breakpoint.
     * @return true if this breakpoint is enabled
     */
    public abstract boolean isEnabled();
    
    /**
     * Enables/disables this breakpoint.
     * If breakpoint isn't enabled, it must behave like there is no breakpoint.
     * @param enabled true when we want to enable breakpoint, false otherwise
     */
    public abstract void setEnabled(boolean enabled);
    
    /**
     * Removes breakpoint. After this operation this breakpoint becomes an unusable.
     */
    public abstract void remove();
    
    /**
     * Sets some handler to breakpoint.
     * @param runnable callable object which will be called every time breakpoint catches some operation
     */
    public abstract void setHandler(Runnable runnable);
    
    /**
     * Returns breakpoint's attached address.
     * @return address of memory cell, to which breakpoint is attached
     */
    public abstract int getAddress();
    
    /**
     * Returns type of this breakpoint.
     * @return one of BREAKPOINT_READ, BREAKPOINT_WRITE, BREAKPOINT_CHANGE
     */
    public abstract int getType();
    
    /**
     * Returns memory cell value from cell, to which this breakpoint is attached.
     * @return memory cell value
     */
    public abstract short getValue();
    
    /**
     * Returns memory to which this breakpoint is attached.
     * @return memory to which this breakpoint is attached
     */
    public abstract Memory16 getMemory();
}
