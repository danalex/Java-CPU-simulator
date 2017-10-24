package cpusimulator.memory;

/**
 * Class represents instruction memory model. It has a capabilities to protect
 * executing code from corruption. Use lock() and unlock() methods to deny or
 * allow to write data to this memory. This memory should be always locked and
 * unlocks for a short time only when we change program code right after
 * assembling process.
 * 
 * @author Daniel Alexandre 2011
 */
public class InstructionMemory16 extends DataMemory16 {
    
    /** Memory write lock */
    private boolean locked;
    /** Callable object which will be called when CPU will attempt to write
     * some data in locked memory region. */
    private Runnable exceptionHandler;
    
    /**
     * Creates new instruction memory model with specified length and invalid operation handlers
     * @param length count of memory cells
     * @param invalidAddressHandler callable object used for handling CPU's program failture
     * @param operationOnLockHandler callable object used for handling CPU's program failture
     * @throws NullPointerException if operationOnLockHandler or invalidAddressHandler is null
     */
    public InstructionMemory16(int length, Runnable invalidAddressHandler, Runnable operationOnLockHandler) {
        super(length, invalidAddressHandler);
        if(operationOnLockHandler == null) throw new NullPointerException();
        exceptionHandler = operationOnLockHandler;
    }
    
    /**
     * Disables data writing to this memory model. It should be called right
     * after code storing process will be complete.
     */
    public void lock() {
        locked = true;
    }
    
    /**
     * Enables data writing to this memory model. It should be called only
     * before code storing process will be started.
     */
    public void unlock() {
        locked = false;
    }
    
    @Override
    public void write(byte address, short value) {
        if(locked) {
            // discard attempt to write there if this memory is locked
            exceptionHandler.run();
        } else {
            super.write(address, value);
        }
    }
}
