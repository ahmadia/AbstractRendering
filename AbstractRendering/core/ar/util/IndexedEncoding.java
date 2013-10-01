package ar.util;

import java.nio.ByteBuffer;

import ar.glyphsets.implicitgeometry.Indexed;
import ar.util.MemMapEncoder.TYPE;

/**Wrapper to interface items encoded using the MemMapEncoder with the implicit geometry system.
 * **/
public class IndexedEncoding implements Indexed {
	private static final long serialVersionUID = 3550855955493381035L;
	
	private final TYPE[] types;
	private final int[] offsets;
	private final int recordOffset;
	private final ByteBuffer buffer;
	
	/**Create a new indexed encoding wrapper for a record in a byte buffer.
	 * This will create a new byte buffer just for the current record (so it will be logically independent of the passed buffer).
	 * 
	 * @param types Data types for the record entries
	 * @param recordOffset relevant record as offset within the given buffer
	 * @param buffer Source buffer
	 * **/
	public IndexedEncoding(final TYPE[] types, int recordOffset, ByteBuffer buffer) {
		this(types, recordOffset, buffer, MemMapEncoder.recordLength(types), MemMapEncoder.recordOffsets(types));
	}
	
	public IndexedEncoding(final TYPE[] types, int recordOffset, ByteBuffer buffer, int recordLength, int[] offsets) {
		this.types = types;
		this.offsets = offsets;
		
		this.recordOffset = 0;
		byte[] bytes = new byte[MemMapEncoder.recordLength(types)];
		buffer.position(recordOffset);
		buffer.get(bytes);
		this.buffer = ByteBuffer.wrap(bytes); 
		
	}

	public Object get(int f) {
		TYPE t = types[f];
		int offset= offsets[f]+recordOffset;
		switch(t) {
			case INT: return buffer.getInt(offset);
			case SHORT: return buffer.getShort(offset);
			case LONG: return buffer.getLong(offset);
			case DOUBLE: return buffer.getDouble(offset);
			case FLOAT: return buffer.getFloat(offset);
			case BYTE: return buffer.get(offset);
			case CHAR: return buffer.getChar(offset);
			case X: throw new IllegalArgumentException("'Skip-type' not supported (denoted 'X'); found at index " + offset);
		}
		throw new IllegalArgumentException("'Unhandled type at offset " + offset);
	}
}
