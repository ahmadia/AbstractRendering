package ar;

import java.io.Serializable;

/**Transfer functions converts an aggregate value into another aggregate value, often a color.
 * 
 * Transfer functions are doing analysis and transformation on the discrete values 
 * represented by a set of aggregates.  Since Abstract Rendering is focused on visualization,
 * many transfer functions directly produce colors (and an image is essentially a set of color aggregates).
 * 
 * However, sometimes producing colors immediately is awkward, so transfer functions
 * are generalized to produce any time of aggregate desired.  Multi-stage transfer
 * is logically the same as single stage, by simple function composition.
 * ar.util.combinators.Chain is a utility for achieving that composition.
 * 
 * Transfer functions have a two-phase life cycle: generic and specialized.
 * Often a transfer function needs information about the data it is about
 * to process before it can process the first pixel (such as the bounds on the values).
 * Generic transfers don't know this information yet, specialized ones do.
 * The "Specialize" method converts a generic function into a specialized one
 * OR a specialized one into another one (specialized transfer functions retain the capabilities of generic ones).  
 * The Transfer.Specialized interface
 * indicates that a transfer function is ready for use.
 * 
 * TODO: Has Specialized-is-transfer-too outlived its usefulness?  I don't know that it is ever used.... 
 *   
 * **/
public interface Transfer<IN,OUT> extends Serializable {
	
	/**What value that represents "empty" from this transfer function?
	 * TODO: with the specialized/item-wise work, this may no longer be needed.  Remove and have Transfer implement BiFunction (replaced 'process' with 'apply')?
	 * */
	public OUT emptyValue();
	
	
	/**Determine control parameter values for the passed set of a aggregates.
	 * This method should be called at least once before the first
	 * value of an aggregate set is presented to the transfer function. 
	 * 
	 * Some transfer functions rely on relationships that exist inside of an 
	 * aggregate set.  This method allows the transfer function to compute
	 * relevant relationship quantities once, and then use them multiple times
	 * in the "at" method.
	 * 
	 * For example, high-definition alpha composition needs to know the maximum
	 * and minimum value in the dataset.  "Specialize" will compute 
	 * those maximum/minimum values.
	 * 
	 * @param aggregates Aggregates to determine the parameters for.
	 * **/
	public default Specialized<IN,OUT> specialize(Aggregates<? extends IN> aggregates) {
		if (this instanceof Transfer.Specialized) {return (Transfer.Specialized<IN, OUT>) this;}
		else {throw new UnsupportedOperationException("Specialization not implemented and default specialization is not applicable.");}
	}

	/**Indicate that a transfer function is "ready to run".
	 * 
	 * By default, all transfers are expected to operate set-wise, taking a whole
	 * set of aggregates in and producing a new set of aggregates.  However, many
	 * transfers can be efficiently composed item-wise (one x/y at a time) and 
	 * may implement the ItemWise interface additionally. 
	 **/
	public static interface Specialized<IN,OUT> extends Transfer<IN,OUT> {
		 /** To facilitate efficient processing of possibly nested transfers,
		  *  the renderer is also taken as an argument.
		  *  
		  *  TODO: Should the return type be <? extends OUT>?  Enables some zero-copy stuff, costs a lot of 'extends...' stuff everywhere
		  **/
		public Aggregates<OUT> process(Aggregates<? extends IN> aggregates, Renderer rend);
	}
 	
	/**What value results from transformation at X/Y?
	 * 
	 * This function accepts the full set of aggregates so context 
	 * (as determined by the full set of aggregates)
	 * can be employed in determining a specific pixel.
	 * 
	 * This function is not guaranteed to be  called from 
	 * a single thread, so implementations must provide for thread safety.
	 */
	public static interface ItemWise<IN,OUT> extends Specialized<IN,OUT> {
		/**Process a single location.  May LOOK at other locations in the input,
		 * but may only UPDATE one position in the output.
		 * 
		 * @return Value that results in translating the input value at position x/y 
		 */
		public OUT at(int x, int y, Aggregates<? extends IN> input);
		
		@Override
		public default Aggregates<OUT> process(Aggregates<? extends IN> aggregates, Renderer rend) {
			return rend.transfer(aggregates, this);
		}
	}
	

}
