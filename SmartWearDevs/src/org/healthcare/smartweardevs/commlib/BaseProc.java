package org.healthcare.smartweardevs.commlib;

public class BaseProc
{
	private final static byte[] hex = "0123456789ABCDEF".getBytes();
	
	public static byte[] subBytes(byte[] src, int begin, int count)
	{
		byte[] bs = new byte[count];
		for (int i = begin; i < begin + count; i++)
			bs[i - begin] = src[i];
		return bs;
	}
	
	public static String Bytes2HexString(byte[] b, int len)
	{
		byte[] buff = new byte[2 * len];
		for (int i = 0; i < len; i++)
		{
			buff[2 * i] = hex[(b[i] >> 4) & 0x0f];
			buff[2 * i + 1] = hex[b[i] & 0x0f];
		}
		return new String(buff);
	}
}
