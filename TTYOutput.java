import javax.sound.sampled.*;

class TTYOutput implements Runnable
{
	static final float kMarkFrequency = 1400f;
	static final float kSpaceFrequency = 1800f;
	static final int kBitsPerSecond = 45;
	static final float kSampleRate = 8000f;
	static final int kBitsPerCharacter = 5;
	static final int kSamplesPerBit = (int) (kSampleRate / kBitsPerSecond);
	private static final int kBufferSize = 2048;	

	interface TTYOutputListener
	{
		public void ttyIsSending(boolean isSending);
	}

	void setListener(TTYOutputListener listener)
	{
		fListener = listener;
	}

	public TTYOutput()
	{
		try
		{
			AudioFormat format = new AudioFormat(kSampleRate, 16, 1, true, true);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, 
                format);
            if (!AudioSystem.isLineSupported(info))
            {
				System.out.println("Line matching " + info + " not supported.");
				return;
			}

			fAudioSink = (SourceDataLine) AudioSystem.getLine(info);
			fAudioSink.open(format, kBufferSize);
		}
		catch (LineUnavailableException exc)
		{
			System.out.println("TTYOutput: unable to open line " + exc);
		}
		
		fMarkAudioBuffer = makeSampleBuffer(kMarkFrequency);
		fSpaceAudioBuffer = makeSampleBuffer(kSpaceFrequency);
		fThread = new Thread(this);
		fThread.start();
 	}

	synchronized public void enqueueString(String message)
	{
		fMessageBuffer.append(message);
		this.notify();
	}

 	private byte[] makeSampleBuffer(float frequency)
 	{
 		byte[] buffer = new byte[kSamplesPerBit * 2];
		for (int i = 0; i < kSamplesPerBit * 2; i = i + 2)
		{
	 		double angle = (frequency * (i / 2) / kSampleRate) * (Math.PI * 2);
			short sample = (short)(Math.sin(angle) * 0x7fff);
			buffer[i] = (byte)((sample >> 8) & 0xff);
			buffer[i + 1] = (byte)(sample & 0xff);
		}

		return buffer;
 	}
	
	private void outputBit(boolean isMark)
	{
		byte[] sourceBuffer = isMark ? fMarkAudioBuffer : fSpaceAudioBuffer;
		int totalWritten = 0;
		while (totalWritten < sourceBuffer.length)
		{
			int thisCount = fAudioSink.write(sourceBuffer, totalWritten, 
				sourceBuffer.length - totalWritten);	
			if (thisCount <= 0)
			{
				System.out.println("write returned zero");
				break;
			}
				
			totalWritten += thisCount;
		}
	}

	// Output a 5 bit code	
	private void outputRawCode(int value)
	{
		outputBit(false);	// Start bit (space)
		for (int bit = 0; bit < kBitsPerCharacter; bit++)
			outputBit((value & (1 << bit)) != 0);
		
		outputBit(true);	// Stop bit (mark)
	}
	
	private void outputCharacter(char c)
	{
		int baudotCode = kUnicodeToBaudot[(int) c];
		if (baudotCode < 0)
			return;	// Skip this character
			
		boolean codeIsFig = (baudotCode & 0x80) != 0;
		if (codeIsFig && !fModeIsFigs)
		{
			fModeIsFigs = true;
			outputRawCode(0x1b);	// Switch to FIGs table
		}
		else if (!codeIsFig && fModeIsFigs)
		{
			fModeIsFigs = false;
			outputRawCode(0x1f);	// Switch to LTRs table
		}
		
		outputRawCode(baudotCode & 0x1f);	// Output the actual character
	}

	public void run()
	{
		fAudioSink.start();

		while (true)
		{
			char c;
			
			synchronized (this) {
				if (fMessageBuffer.length() == 0)
				{
					if (fListener != null)
					{
						// Make sure we've actually finished sending samples
						// before informing listener.
						fAudioSink.drain();
						fListener.ttyIsSending(false);
					}

					do
					{
						try 
						{
							this.wait();
						} 
						catch (InterruptedException exc)
						{
						}
					} while (fMessageBuffer.length() == 0);

					if (fListener != null)
						fListener.ttyIsSending(true);
				}
				
				c = fMessageBuffer.charAt(0);
				fMessageBuffer.deleteCharAt(0);
			}
			
			outputCharacter(c);
		}
	}

	private StringBuffer fMessageBuffer = new StringBuffer();
	private boolean fModeIsFigs = false;
	private SourceDataLine fAudioSink;
	private Thread fThread;
	private byte[] fMarkAudioBuffer;
	private byte[] fSpaceAudioBuffer;
	private TTYOutputListener fListener;
	
	// Maps a 7 bit unicode code point to a 5 bit baudot code.  A -1 in this
	// table indicates no mapping.  If the 7th bit is set (0x80), then this
	// is in the FIG table, otherwise it is in the LTR table.
	static final int[] kUnicodeToBaudot = {
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 130, -1, -1, 136, -1, -1, -1, 
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 132, 141, 
		145, -1, 137, -1, -1, 139, 143, 146, -1, 154, 140, 133, 156, 157, 150, 
		151, 147, 129, 138, 144, 149, 135, 134, 152, 142, 158, -1, 148, -1, 
		153, -1, 3, 25, 14, 9, 1, 13, 26, 20, 6, 11, 15, 18, 28, 12, 24, 22, 23, 
		10, 5, 16, 7, 30, 19, 29, 21, 17, -1, -1, -1, -1, -1, -1, 3, 25, 14, 9, 
		1, 13, 26, 20, 6, 11, 15, 18, 28, 12, 24, 22, 23, 10, 5, 16, 7, 30, 19, 
		29, 21, 17, -1, -1, -1, -1, -1
	};
}
