package org.healthcare.smartweardevs.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.healthcare.smartweardevs.commlib.BaseProc;
import org.healthcare.smartweardevs.devices.DeviceInterface;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

public class BluetoothManager
{
	private static final String TAG = "BluetoothManager";

	private BluetoothAdapter mBluetoothAdt = null;

	// Unique UUID for this application
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private List<String> lstDevices = new ArrayList<String>();
	private static ArrayAdapter<String> adtDevices;

	private Activity mActivityUsed = null;
	private ConnectThread mConnectThread = null;
	private CommThread mCommThread = null;

	private DeviceInterface mDevIf = null;
	private static final int BYTE_NUM = 64;
	private static byte[] mReadBuff = new byte[BYTE_NUM];

	private void connectionFailed()
	{
		Log.i(TAG, "connectionFailed");

	}

	private void connectionLost()
	{
		Log.i(TAG, "connectionLost");

	}

	private void connected(BluetoothSocket socket, BluetoothDevice device)
	{
		Log.d(TAG, "connected");

		if (null != mCommThread)
		{
			if (mCommThread.isAlive()==true)
			{
				Log.i(TAG, "mCommThread alive, need killed");
				try
				{
					mCommThread.interrupt();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					Log.e(TAG, "mCommThread.interrupt exception");
				}
			}
			
			mCommThread.cancel();
		}
		
		mCommThread = new CommThread(socket);
		mCommThread.start();
		
	}

	private class ConnectThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device)
		{
			mmDevice = device;
			BluetoothSocket tmp = null;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try
			{
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			}
			catch (IOException e)
			{
				Log.e(TAG, "createRfcommSocketToServiceRecord failed", e);
			}
			mmSocket = tmp;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN ConnectThread");
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			boolean ans = mBluetoothAdt.cancelDiscovery();
			if (false == ans)
			{
				Log.e(TAG, "cancelDiscovery fail!");
			}
			// Make a connection to the BluetoothSocket
			try
			{
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
				Log.i(TAG, "connect succ");
			}
			catch (IOException e)
			{
				connectionFailed();
				// Close the socket
				try
				{
					mmSocket.close();
				}
				catch (IOException e2)
				{
					Log.e(TAG,
							"unable to close() socket during connection failure",
							e2);
				}
				return;
			}

			connected(mmSocket, mmDevice);

		}

		public void cancel()
		{	
			try
			{
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class CommThread extends Thread
	{
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public CommThread(BluetoothSocket socket)
		{
			Log.d(TAG, "create CommThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try
			{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}
			catch (IOException e)
			{
				Log.e(TAG, "get stream of socket fail", e);

			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run()
		{
			Log.i(TAG, "BEGIN CommThread");

			boolean ans = mmSocket.isConnected();
			if (true == ans)
			{
				Log.i(TAG, "in connected:");
				Log.i(TAG, mmSocket.getRemoteDevice().getAddress());

			}
			else
			{
				Log.e(TAG, "not connected");
				return;
			}

			if (null == mDevIf)
			{
				Log.e(TAG, "not set device");
				return;
			}

			Log.i(TAG, "request");

			this.write(mDevIf.getReqCommand());

			Log.i(TAG, "response");

			this.read(mReadBuff);

			Log.i(TAG, "stop");
			this.write(mDevIf.getStopCommand());

			// ommit stop-response
			try
			{
				mmInStream.read(mReadBuff);
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			this.cancel();
		}

		/**
		 * Write to the connected OutStream.
		 * 
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer)
		{
			try
			{
				mmOutStream.write(buffer);
			}
			catch (IOException e)
			{
				Log.e(TAG, "Exception during write", e);
			}
		}

		public void read(byte[] readBuff)
		{
			// Keep listening to the InputStream while connected
			while (true)
			{
				try
				{
					// Read from the InputStream
					int nBytes = mmInStream.read(readBuff);

					Log.d(TAG,
							nBytes
									+ ":"
									+ BaseProc
											.Bytes2HexString(readBuff, nBytes));

					if (0 == nBytes)
						;
					else
						mDevIf.parseResponse(readBuff, nBytes);

					if (true == mDevIf.IsEnd())
					{
						// stop
						Log.i(TAG, "finish getting last result");
						mDevIf.reset();
						break;
					}
				}
				catch (IOException e)
				{
					Log.e(TAG, "disconnected", e);
					connectionLost();
					mDevIf.reset();
					break;
				}
			}
		}

		public void cancel()
		{
			Log.i(TAG, "conn-socket close!");
			try
			{
				mmOutStream.write(-1);
				mmSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * 
	 */
	private BroadcastReceiver searchDevices = new BroadcastReceiver()
	{

		public void onReceive(Context context, Intent intent)
		{

			String action = intent.getAction();
			Bundle b = intent.getExtras();

			Object[] lstName = b.keySet().toArray();

			// 显示所有收到的消息及其细节
			for (int i = 0; i < lstName.length; i++)
			{

				String keyName = lstName[i].toString();
				Log.i(TAG + "|" + keyName, String.valueOf(b.get(keyName)));

			}

			// 搜索远程蓝牙设备时，取得设备的MAC地址
			if (BluetoothDevice.ACTION_FOUND.equals(action))
			{

				// 代表远程蓝牙适配器的对象取出
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				String str = device.getName() + "|" + device.getBondState()
						+ "|" + device.getAddress();

				// 防止重复添加
				if (lstDevices.indexOf(str) == -1)
				{
					lstDevices.add(str); // 获取设备名称和mac地址
					Log.i(TAG, str);

				}

				// 起到更新的效果
				// adtDevices.notifyDataSetChanged();

			}

		}
	};

	/**
	 * 
	 * @Title: setActivity
	 * @Description: for doing some things related with activity
	 * @param @param actv
	 * @return void
	 * @throws
	 */
	public void setActivity(Activity actv)
	{
		mActivityUsed = actv;
	}

	public boolean initMgr()
	{
		// 获得BluetoothAdapter对象
		
		mBluetoothAdt = BluetoothAdapter.getDefaultAdapter();
		if (null == mBluetoothAdt)
		{
			Log.e(TAG, "getDefaultAdapter fail!");
			return false;
		}

		return true;
	}

	public boolean finMgr()
	{
		if (null == mActivityUsed)
			return false;
		try
		{
			mActivityUsed.unregisterReceiver(searchDevices);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.e(TAG, "unregisterReceiver(searchDevices) fail");
		}
		mActivityUsed = null;
		return true;

	}

	public boolean openLocalBluetooth()
	{
		if (null == mBluetoothAdt)
		{
			Log.e(TAG, "mBluetoothAdt is null");
			return false;
		}

		boolean isOpened = (mBluetoothAdt != null && mBluetoothAdt.isEnabled());

		if (isOpened)
		{
			Log.w(TAG, "bluetooth was already opened");
			return true;
		}

		// 打开Bluetooth设备 这个无提示效果
		/*
		 * boolean bEnb = mBluetoothAdt.enable(); if (false == bEnb) {
		 * Log.e(TAG, "mBluetoothAdt enable fail!"); return bEnb; }
		 */
		Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

		if (null != mActivityUsed)
			mActivityUsed.startActivity(intent);

		return true;

	}

	public boolean closeLocalBluetooth()
	{
		if (mBluetoothAdt == null)
		{

			Log.e(TAG, "mBluetoothAdt is null");
			return false;

		}
		else if (mBluetoothAdt.isEnabled())
		{
			// 清空搜索的列表

			lstDevices.clear();

			// 起到更新的效果

			// adtDevices.notifyDataSetChanged();

			// 关闭蓝牙

			mBluetoothAdt.disable();

			Log.i(TAG, "mBluetoothAdt disable");

		}

		return true;
	}

	public boolean searchBtDevices()
	{
		if (mBluetoothAdt == null)
		{

			Log.e(TAG, "mBluetoothAdt is null");
			return false;

		}

		if (null == mActivityUsed)
		{
			Log.e(TAG, "mActivityUsed is null");
			return false;
		}

		Log.i(TAG, "search bluetooth devices now");
		if (mBluetoothAdt.getState() == BluetoothAdapter.STATE_OFF)
		{
			// 如果蓝牙还没开启
			Log.e(TAG, "mBluetoothAdt not opened");
			// use some constant err code: better! for app layer processing
			 return false;
		}

		// 注册Receiver来获取蓝牙设备相关的结果 将action指定为：ACTION_FOUND
		IntentFilter intent = new IntentFilter();

		intent.addAction(BluetoothDevice.ACTION_FOUND);// 用BroadcastReceiver来取得搜索结果
		intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

		// 注册广播接收器
		mActivityUsed.registerReceiver(searchDevices, intent);

		// 防止重复添加的数据
		lstDevices.clear();

		// 扫描蓝牙设备 最少要12秒，功耗也非常大（电池等） 是异步扫描意思就是一调用就会扫描
		return mBluetoothAdt.startDiscovery();
	}

	/**
	 * 
	 * @Title: communication
	 * @Description: TODO
	 * @param @param devMAC
	 * @return void
	 * @throws
	 */
	public void communication(String devMAC)
	{		
		Log.i(TAG, "connect to " + devMAC + ": request and get response");
		BluetoothDevice device = mBluetoothAdt.getRemoteDevice(devMAC);
		if (null != mConnectThread)
		{
			if (mConnectThread.isAlive()==true)
			{
				Log.i(TAG, "mConnectThread alive, need killed");
				try
				{
				mConnectThread.interrupt();
				}
				catch (Exception e)
				{
					e.printStackTrace();
					Log.e(TAG, "mConnectThread.interrupt exception");
				}
			}
			
			mConnectThread.cancel();
		}
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
	}

	/**
	 * 
	 * @Title: getBluezAdt
	 * @Description: app layer can do other things that the lib not afford. it
	 *               seems not a good idea.
	 * @param @return
	 * @return BluetoothAdapter
	 * @throws
	 */
	public BluetoothAdapter getBluezAdt()
	{
		return mBluetoothAdt;
	}

	/**
	 * 
	 * @Title: getDevMAC
	 * @Description: by DevName + boundinfo(avoid get wrong dev when exist same
	 *               devname)
	 * @param @param DevName
	 * @param @return
	 * @return String
	 * @throws
	 */
	public String getDevMAC(String DevName)
	{
		for (Iterator<String> iter = lstDevices.iterator(); iter.hasNext();)
		{
			String content = iter.next();
			Log.i(TAG, content);

			if (0 == content.indexOf(DevName, 0))
			{
				int sIndex = content.indexOf('|');
				sIndex = sIndex + 1;

				int eIndex = content.lastIndexOf('|');

				String devState = content.substring(sIndex, eIndex);
				Log.i(TAG, DevName + " devstate:" + devState);
				int iState = Integer.parseInt(devState);

				if (iState != BluetoothDevice.BOND_BONDED)
				{
					// 未配对同名设备

				}
				else
				{
					String tmp = content.substring(eIndex + 1);
					Log.i(TAG, DevName + ":" + tmp);
					return tmp;
				}
			}

		}

		return null;
	}

	public void setDevice(DeviceInterface devIf)
	{
		mDevIf = devIf;
	}

}
