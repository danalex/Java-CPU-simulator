package cpusimulator.code;

/**
 * This exception should be thrown in some mistaken cases during assembling
 * ASM code to processor code.
 * 
 * @author Daniel Alexandre 2011
 */
public class AssembleException extends Exception {
    
    /**
     * Creates new assemble exception without details and enclosed exception.
     */
    public AssembleException() {
        super();
    }
    
    /**
     * Creates new assemble exception with details but without enclosed exception.
     * @param message message about exception
     */
    public AssembleException(String message) {
        super(message);
    }
    
    /**
     * Creates new assemble exception wit details and enclosed exception.
     * @param message message about exception
     * @param cause exception by which this exception is caused
     */
    public AssembleException(String message, Throwable cause) {
        super(message, cause);
    }
}
