package org.healthcare.smartweardevs.devices;

public interface DeviceInterface
{
	public byte[] getReqCommand();
	public void	  parseResponse(byte[] rep, int len);
	public void   reset();
	public boolean IsEnd();
	public byte[] getStopCommand();
	public void   setHealthDataListener(HealthDataListener listener);
	public boolean IsValid();
} 
