package org.healthcare.smartweardevs.devices;

import java.util.Arrays;

import org.healthcare.smartweardevs.commlib.BaseProc;
import org.healthcare.smartweardevs.commlib.CRC8;

import android.util.Log;

public class BloodPressureModule implements DeviceInterface
{
	private static final String TAG = "BloodPressureModule";
	// HANDSHAKE_COMMAND not used?
	static final byte[] HANDSHAKE_COMMAND = new byte[] { (byte) 0xBE,
			(byte) 0xB0, 0x01, (byte) 0xB0, (byte) 0xCE };

	static final byte[] START_TESTING_COMMAND = new byte[] { (byte) 0xBE,
			(byte) 0xB0, 0x01, (byte) 0xC0, (byte) 0x36 };

	static final byte[] STOP_TESTING_COMMAND = new byte[] { (byte) 0xBE,
			(byte) 0xB0, 0x01, (byte) 0xC1, (byte) 0x68 };

	static final byte[] SYSTEM_SLEEP_COMMAND = new byte[] { (byte) 0xBE,
			(byte) 0xB0, 0x01, (byte) 0xD0, (byte) 0xAB };

	private static final byte[] HEAD_ID = new byte[] { (byte) 0xD0, (byte) 0xC2 };
	private static final int MSG_LEN_BYTENUM = 1;
	private static final int MSG_STATUS_BYTENUM = 1;
	private static final byte[] RESP_SUCC = new byte[] { 0x00, 0x00, 0x2F };
	private static final byte[] RESP_BUSY = new byte[] { (byte) 0xFF,
			(byte) 0xFF, (byte) 0x9B };
	private static final byte[] RESP_INVALID = new byte[] { 0x00, (byte) 0xFF,
			0x1A };

	private static final byte[] DOING_STATUS = new byte[] { (byte) 0xCB };
	private static final byte[] RESULT_STATUS = new byte[] { (byte) 0xCC };

	private static final int BUFF_NUM = 64;
	private static byte[] mBuffer = new byte[BUFF_NUM];

	private static int mIndex = 0; // input index
	private static int mCurIndex = 0; // read next index

	private static boolean mValid = true;
	private static boolean mResp = false;
	private static boolean mFin = false;

	private HealthDataListener mListener = null;

	public static final int DOING_DATATYPE = 0;
	public static final int RESULT_DATATYPE = 1;

	public class BloodPressureData
	{
		public byte[] mPress = new byte[2]; // 2: pressure occupy two bytes
		public byte mHeart = 0;

		public short PressShortValue()
		{
			return (short) ((((short) (mPress[0]) << 8) & 0xFF00) | (mPress[1] & 0xFF));
		}

		public byte Systolic = 0;
		public byte Diastolic = 0;
		public byte Pulse = 0;
		public byte HeartAnomaly = 0;

	}

	private BloodPressureData mBPVal = new BloodPressureData();

	public BloodPressureData getBPVal()
	{
		return mBPVal;
	}

	private void doClear()
	{
		mIndex = 0;
		mCurIndex = 0;
	}

	private void finish()
	{
		doClear();
		mValid = true;
		mResp = false;
		mFin = false;
	}

	private void moveBuff()
	{
		if (mCurIndex == mIndex)
		{
			mCurIndex = 0;
			mIndex = 0;
			return;
		}
		System.arraycopy(mBuffer, mCurIndex, mBuffer, 0, mIndex - mCurIndex);
		mIndex = mIndex - mCurIndex;
		mCurIndex = 0;

		Log.d(TAG, "index:" + mIndex + " curIndex:" + mCurIndex);

		String debuginfo = BaseProc.Bytes2HexString(mBuffer, mIndex);

		Log.d(TAG, debuginfo);

	}

	@Override
	public byte[] getReqCommand()
	{
		// TODO Auto-generated method stub
		return START_TESTING_COMMAND;
	}

	@Override
	public void parseResponse(byte[] rep, int len)
	{
		// TODO Auto-generated method stub
		if (false == mValid)
			return;

		System.arraycopy(rep, 0, mBuffer, mIndex, len);
		mIndex = mIndex + len;

		if (mIndex >= HEAD_ID.length + MSG_LEN_BYTENUM)
		{

			if (mCurIndex < HEAD_ID.length + MSG_LEN_BYTENUM)
			{
				// 1. msg head
				Log.i(TAG, "msg-head parse:");
				int i = HEAD_ID.length;
				while (--i >= 0)
				{
					if (mBuffer[i] != HEAD_ID[i])
					{
						Log.e(TAG, "get invalid headid! (" + i + ") th byte:"
								+ mBuffer[i]);
						mValid = false;
						return;
					}
				}

				Log.i(TAG, "msg-head check succ");
			}

			// 2.read msg len byte
			// the below 2 line code repeatily exec.
			Log.i(TAG, "msg-data parse");

			int dataLen = mBuffer[HEAD_ID.length];// MSG_LEN_BYTENUM == 1

			mCurIndex = HEAD_ID.length + MSG_LEN_BYTENUM;

			if (mIndex > HEAD_ID.length + MSG_LEN_BYTENUM + dataLen)
			{
				// include crc8
				// crc8 occupy one byte default

				if (false == mResp)
				{
					Log.i(TAG, "response parse begin");
					// process response
					byte[] res = BaseProc.subBytes(mBuffer, mCurIndex,
							dataLen + 1);
					boolean bSucc = Arrays.equals(res, RESP_SUCC);
					if (false == bSucc)
					{
						if (true == Arrays.equals(res, RESP_BUSY))
						{
							Log.e(TAG, "bp busy!");
						}
						else if (true == Arrays.equals(res, RESP_INVALID))
						{
							Log.e(TAG, "invalid command");
						}
						else
						{
							Log.e(TAG, "unknow error");
						}

						// do some clear
						doClear();
					}
					else
					{
						// succ
						Log.i(TAG, "response parse succ");
						mResp = true;
						mCurIndex = HEAD_ID.length + MSG_LEN_BYTENUM + dataLen
								+ 1;
						moveBuff();

					}

					return;
				}

				// 0xCB 0xCC
				// status occupy one byte default
				if (DOING_STATUS[0] == mBuffer[mCurIndex])
				{
					Log.i(TAG, "DOING MSG parse begin:");
					int contentLen = dataLen - MSG_STATUS_BYTENUM;
					mCurIndex = mCurIndex + MSG_STATUS_BYTENUM;
					System.arraycopy(mBuffer, mCurIndex, mBPVal.mPress, 0,
							mBPVal.mPress.length);
					mCurIndex = mCurIndex + mBPVal.mPress.length;

					mBPVal.mHeart = mBuffer[mCurIndex];

					Log.i(TAG, "press:" + mBPVal.PressShortValue() + ", heart:"
							+ mBPVal.mHeart);

					if (null == mListener)
						Log.e(TAG, "mListener null");
					else
						mListener.recvHealthData(DOING_DATATYPE);

					// 3 health-data
					if (3 != contentLen)
					{
						Log.e(TAG, "contentLen " + contentLen + " error");
					}

					// check crc8
					mCurIndex = mCurIndex + 1;
					byte realCrc8 = CRC8.calcCrc8(mBuffer, 0, HEAD_ID.length
							+ MSG_LEN_BYTENUM + dataLen);
					if (realCrc8 == mBuffer[mCurIndex])
					{
						Log.i(TAG, "crc8 check succ");
					}
					else
					{
						Log.e(TAG,
								"crc8 check fail, data-transmit happen exception!");
					}

					mCurIndex = HEAD_ID.length + MSG_LEN_BYTENUM + dataLen + 1;
					moveBuff();

				}

				if (RESULT_STATUS[0] == mBuffer[mCurIndex])
				{
					Log.i(TAG, "result MSG parse begin:");
					int contentLen = dataLen - MSG_STATUS_BYTENUM;
					mCurIndex = mCurIndex + MSG_STATUS_BYTENUM;

					mBPVal.Diastolic = mBuffer[mCurIndex];

					mCurIndex = mCurIndex + 1;

					mBPVal.Systolic = mBuffer[mCurIndex];

					mCurIndex = mCurIndex + 1;

					mBPVal.Pulse = mBuffer[mCurIndex];

					mCurIndex = mCurIndex + 1;

					mBPVal.HeartAnomaly = mBuffer[mCurIndex];

					// 4 health-data
					if (4 != contentLen)
					{
						Log.e(TAG, "contentLen " + contentLen + " error");
					}

					short Diastolic = (short) (mBPVal.Diastolic & 0xFF);
					short Systolic = (short) (mBPVal.Systolic & 0xFF);
					short Pulse = (short) (mBPVal.Pulse & 0xFF);
					Log.i(TAG, "health value:" + Diastolic + "," + Systolic
							+ "," + Pulse);

					if (null == mListener)
						Log.e(TAG, "mListener null");
					else
						mListener.recvHealthData(RESULT_DATATYPE);

					// check crc8
					mCurIndex = mCurIndex + 1;
					byte realCrc8 = CRC8.calcCrc8(mBuffer, 0, HEAD_ID.length
							+ MSG_LEN_BYTENUM + dataLen);
					if (realCrc8 == mBuffer[mCurIndex])
					{
						Log.i(TAG, "crc8 check succ");
					}
					else
					{
						Log.e(TAG,
								"crc8 check fail, data-transmit happen exception!");
					}
					mFin = true;
				}
			}
		}
	}

	@Override
	public void reset()
	{
		// TODO Auto-generated method stub
		finish();
	}

	@Override
	public boolean IsEnd()
	{
		// TODO Auto-generated method stub
		return mFin;
	}

	@Override
	public byte[] getStopCommand()
	{
		// TODO Auto-generated method stub
		return STOP_TESTING_COMMAND;
	}

	@Override
	public void setHealthDataListener(HealthDataListener listener)
	{
		// TODO Auto-generated method stub
		mListener = listener;
	}

}
