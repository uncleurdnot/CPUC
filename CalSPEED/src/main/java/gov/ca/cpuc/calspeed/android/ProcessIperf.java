/*
Copyright (c) 2013, California State University Monterey Bay (CSUMB).
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice,
       this list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above
           copyright notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.

    3. Neither the name of the CPUC, CSU Monterey Bay, nor the names of
       its contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package gov.ca.cpuc.calspeed.android;

import gov.ca.cpuc.calspeed.android.Constants;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;

import android.util.Log;

public final class ProcessIperf {
	public Boolean isPhase2;
	public Float phase1UploadResult;
	public Float phase1DownloadResult;
	public Boolean isUDP;
	public Boolean udpSuccess;
	public String udpMessage;
	public String jitter;
	public String loss;
	public String period; // in seconds
	private Integer state;
	public Float uploadSpeed;
	public Boolean uploadSuccess;
	public String uploadMessage;
	public Float[] rollingUploadSpeed = new Float[4];
	public Integer[] rollingUploadCount = new Integer[4];
	public Boolean[] rollingUploadDone = new Boolean[4];
	public Boolean finishedUploadTest;
	public Boolean printedUploadResults;
	public Float downloadSpeed;
	public Boolean downloadSuccess;
	public String downloadMessage;
	public Float down;
	public Boolean downFinalStatusFailed;
	public Float[] rollingDownloadSpeed = new Float[4];
	public Integer[] rollingDownloadCount = new Integer[4];
	public Boolean[] rollingDownloadDone = new Boolean[4];
	private Integer[] iperfThreadState = new Integer[Constants.IPERF_TCP_THREADS + 1];
	public String server_location;
	private AndroidUiServices uiServices;

	public ProcessIperf(String period, String server,
			AndroidUiServices uiServices) {
		jitter = " NA";
		loss = "NA";
		this.period = period;
		state = 0;
		isUDP = true;
		udpMessage = "Delay Variation";
		udpSuccess = true;
		server_location = server;
		finishedUploadTest = false;
		printedUploadResults = false;
		this.uiServices = uiServices;
		this.isPhase2 = false;
	}

	public ProcessIperf(String server, AndroidUiServices uiServices) {
		isUDP = false; // is TCP test
		finishedUploadTest = false;
		printedUploadResults = false;
		uploadSuccess = true;
		downloadSuccess = true;
		this.uiServices = uiServices;

		state = 0;
		for (int i = 0; i < iperfThreadState.length; i++) {
			iperfThreadState[i] = 0;
		}

		for (int i = 0; i < 4; i++) {
			rollingUploadSpeed[i] = 0.0f;
			rollingUploadCount[i] = 0;
			rollingUploadDone[i] = false;
			rollingDownloadSpeed[i] = 0.0f;
			rollingDownloadCount[i] = 0;
			rollingDownloadDone[i] = false;
		}
		uploadSpeed = 0.0f;
		downloadSpeed = 0.0f;
		isPhase2 = false;
		phase1UploadResult = 0.0f;
		phase1DownloadResult = 0.0f;

		uploadMessage = "Upload Speed";
		downloadMessage = "Download Speed";

		server_location = server;
	}

	public void setUDPPhase2() {
		this.isPhase2 = true;
	}

	public void setTCPPhase2(Float uploadResult, Float downloadResult) {
		this.isPhase2 = true;
		phase1UploadResult = uploadResult;
		phase1DownloadResult = downloadResult;
	}

	public static String formatFloatString(String value) {
		Float flAverage = Float.valueOf(value);
		NumberFormat numberFormat = new DecimalFormat("#.0");
		return (numberFormat.format(flAverage));
	}

	public Boolean AllUploadThreadsDone() {
		Boolean allDone = true;
		for (int i = 0; i < 4; i++) {
			if (!rollingUploadDone[i]) {
				allDone = false;
			}
		}

		return (allDone);
	}

	public void SetIperfUploadSpeedOnly() {

		if (AllUploadThreadsDone()) {
			finishedUploadTest = true;

			if (isPhase2) {
				SetFinalUploadSpeed();
			}//uploadSpeed *= 100f; //set at the end of phase 1 & also after phase 2
			uiServices.setResults(Constants.THREAD_WRITE_UPLOAD_DATA,
					uploadMessage, formatFloatString(uploadSpeed.toString()),
					!uploadSuccess, !uploadSuccess);
			if (Constants.UploadDebug) {
				Log.v("debug", "in SetIPerfUploadSpeedOnly uploadspeed="
						+ uploadSpeed.toString());
			}
			uiServices.setUploadNumberStopTimer(formatFloatString(uploadSpeed
					.toString()));
			MoveProgressBar(2);
			uiServices.startDownloadTimer();
		} else {
			displayUploadRollingAverage();
		}

	}

	private void MoveProgressBar(Integer increment) {
		uiServices.incrementProgress(2);
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
			// do nothing
		}
	}

	public final void SetUDPIperfFinalStatus() {
		// set success status only--failed test already set

		if (this.jitter.contains("NA")) {
			this.udpSuccess = false;
			udpMessage = "Delay Variation Incomplete";
		}
		if (udpSuccess) {
			udpMessage = "Delay Variation";
		}
		if (!isPhase2) {
			uiServices.setResults(Constants.THREAD_WRITE_JITTER_DATA,
					udpMessage, jitter, !udpSuccess, !udpSuccess);
		}
	}

	public final void SetPhase1TCPIperfStatus() {
		Boolean printDownloadSpeed = true;
		Boolean printUploadSpeed = true;

		finishedUploadTest = true;
		if (uploadSuccess) {
			if (!downloadSuccess) { // either error or timeout

				for (int i = 0; i < 4; i++) {
					if ((rollingDownloadCount[i] > 0)
							&& !rollingDownloadDone[i])
						downloadSpeed += rollingDownloadSpeed[i]
								/ rollingDownloadCount[i];
				}
				if (downloadSpeed == 0.0f) {
					printDownloadSpeed = false;
					downloadMessage = "Download Incomplete";
				} else {
					printDownloadSpeed = true;
					downloadMessage = "Download Speed";
				}
			}
		} else {
			MoveProgressBar(2);
			downloadSuccess = false;
			printDownloadSpeed = false;
			downloadMessage = "Download Incomplete";
			for (int i = 0; i < 4; i++) {
				if ((rollingUploadCount[i] > 0) && !rollingUploadDone[i])
					uploadSpeed += rollingUploadSpeed[i]
							/ rollingUploadCount[i];
			}
			if (uploadSpeed == 0.0f) {
				printUploadSpeed = false;
				uploadMessage = "Upload Incomplete";
			} else {
				printUploadSpeed = true;
				uploadMessage = "Upload Speed";
			}

		}
		//uploadSpeed *= 100f; //gets added at beginning of phase 2
		uiServices.setResults(Constants.THREAD_WRITE_UPLOAD_DATA,
				uploadMessage, formatFloatString(uploadSpeed.toString()),
				!printUploadSpeed, !printUploadSpeed);
		if (!uploadSuccess) {
			if (Constants.UploadDebug) {
				Log.v("debug",
						"in TCP end of phase 1 setUploadNumberStopTimer uploadspeed="
								+ uploadSpeed.toString());
			}
			uiServices.setUploadNumberStopTimer(formatFloatString(uploadSpeed
					.toString()));
		}
		//downloadSpeed *= 100f; //gets added at the end of phase 1
		uiServices.setResults(Constants.THREAD_WRITE_DOWNLOAD_DATA,
				downloadMessage, formatFloatString(downloadSpeed.toString()),
				!printDownloadSpeed, !printDownloadSpeed);

		uiServices.setDownloadNumberStopTimer(formatFloatString(downloadSpeed
				.toString()));
	}

	private void SetFinalUploadSpeed() {
		Float up = 0.0f;

		if (!uploadSuccess) {
			uploadMessage = "Upload Speed";
			uploadSuccess = true;
			downloadSuccess = false;
			downloadMessage = "Download Incomplete";
			for (int i = 0; i < 4; i++) {
				if ((rollingUploadCount[i] > 0) && !rollingUploadDone[i])
					uploadSpeed += rollingUploadSpeed[i]
							/ rollingUploadCount[i];
			}
			up = phase1UploadResult + uploadSpeed;
			if ((phase1UploadResult != 0.0f) && (uploadSpeed != 0.0f)) {
				up = up / 2; // take the average if any both are non-zero
			}
			if ((phase1UploadResult == 0.0f) && (uploadSpeed == 0.0f)) {
				uploadSuccess = false;
				uploadMessage = "Upload Incomplete";
			}
			uploadSpeed = up;
		} else {
			uploadMessage = "Upload Speed";
			if (phase1UploadResult != 0.0f) {
				up = (phase1UploadResult + uploadSpeed) / 2;
			} else {
				up = uploadSpeed;
			}
			uploadSpeed = up;
		}
	}

	public void SetIperfTCPAvgFinal() {
		down = 0.0f;

		String downFinalMessage = "Download Speed";
		downFinalStatusFailed = false;
		//these only become active is one of the tests fail
		if (!uploadSuccess) {
			SetFinalUploadSpeed();
			MoveProgressBar(2);
			uiServices.setResults(Constants.THREAD_WRITE_UPLOAD_DATA,
					uploadMessage, formatFloatString(uploadSpeed.toString()),
					!uploadSuccess, !uploadSuccess);
			uiServices.setUploadNumberStopTimer(formatFloatString(uploadSpeed
					.toString()));
		}

		if (!downloadSuccess) {
			for (int i = 0; i < 4; i++) {
				if ((rollingDownloadCount[i] > 0) && !rollingDownloadDone[i])
					downloadSpeed += rollingDownloadSpeed[i]
							/ rollingDownloadCount[i];
			}

			down = phase1DownloadResult + downloadSpeed;
			if ((phase1DownloadResult != 0.0f) && (downloadSpeed != 0.0f)) {
				down = down / 2; // take the average if any both are non-zero
			}
			if ((phase1DownloadResult == 0.0f) && (downloadSpeed == 0.0f)) {
				downFinalStatusFailed = true;
				downFinalMessage = "Download Incomplete";
			}
		} else {
			if (phase1DownloadResult != 0.0f) {
				down = (phase1DownloadResult + downloadSpeed) / 2;
			} else {
				down = downloadSpeed;
			}
		}
		if (Constants.DownloadDebug)
			Log.v("debug", "down=" + down.toString() + " downloadsuccess="
					+ downloadSuccess.toString() + " downloadspeed="
					+ downloadSpeed.toString()); //down *= 100f; //gets added at end of phase2
		uiServices.setResults(Constants.THREAD_WRITE_DOWNLOAD_DATA,
				downFinalMessage,
				ProcessIperf.formatFloatString(down.toString()),
				downFinalStatusFailed, downFinalStatusFailed);
		uiServices
				.setDownloadNumberStopTimer(formatFloatString(down.toString()));

	}

	private final void ParseUDPLine(String line) {
		int indexStart = -1;
		int indexEnd = -1;

		switch (state) {
		case 0:
			indexStart = line.indexOf("Server Report");
			if (indexStart != -1) {
				state = 1;
			}
			break;
		case 1:
			indexStart = line.indexOf("/sec");
			if (indexStart != -1) {
				indexEnd = line.indexOf("ms", indexStart);
				if (indexEnd != -1) {
					jitter = line.substring(indexStart + 5, indexEnd - 1);
					indexStart = line.indexOf("(");
					indexEnd = line.indexOf(")", indexStart);
					loss = line.substring(indexStart + 1, indexEnd - 1);
					Log.i("INFO", "udp loss" +  loss);
					MOSCalculation.addUDPLoss(Double.parseDouble(loss));
					state = 2;
				}
			}
			break;
		default:
			break;
		}
	}

	private Integer getThreadNum(String line) {
		int indexStart = -1;
		int indexEnd = -1;
		int threadNum = -1;
		String threadNumStr = null;

		try {
			indexStart = line.indexOf("[");
			if ((indexStart != -1) && (line.indexOf("[SUM]") == -1)) {
				indexEnd = line.indexOf("]");
				if (indexEnd != -1) {
					threadNumStr = line.substring(indexStart + 2, indexEnd);
					threadNumStr = threadNumStr.replaceAll("^\\s+", "");
					if (Constants.DEBUG)
						System.out.println("threadstring=" + threadNumStr);
					threadNum = Integer.parseInt(threadNumStr);
				}
			}
		} catch (Exception e) {
			return (-1);
		}
		return (threadNum);
	}

	private Float getTCPBitsPerSec(String line) {
		int indexStart = -1;
		int indexEnd = -1;
		float currentSpeed = 0.0f;

		indexStart = line.indexOf("KBytes");
		if (indexStart != -1) {
			indexEnd = line.indexOf("Kbits/sec");
			String upspeed = line.substring(indexStart + 7, indexEnd);
			try {
				currentSpeed = Float.parseFloat(upspeed);
				if (currentSpeed > Constants.IPERF_BIG_NUMBER_ERROR) {
					currentSpeed = 0.0f;
				}
				return (currentSpeed);
			} catch (Exception e) {
				// ignore if not a number
				return (0.0f);
			}
		} else {
			return (currentSpeed);
		}
	}

	public void displayUploadRollingAverage() {
		Float rollingAvg = 0.0f;
		Integer i = 0;
		rollingAvg = phase1UploadResult + uploadSpeed; // phase1UploadResult is
														// 0 for phase 1
		try {
			for (i = 0; i < 4; i++) {
				if (!rollingUploadDone[i]) {
					if (rollingUploadCount[i] != 0) {
						rollingAvg += rollingUploadSpeed[i]
								/ rollingUploadCount[i];
					}
				}
			}
		} catch (Exception e) {
			if (Constants.DEBUG)
				System.out.println("In displayUploadRollingAverage i ="
						+ i.toString());
		}
		if (isPhase2) {
			rollingAvg = rollingAvg / 2;
		} //rollingAvg *= 0.0009765625f; //gets multiplied while rolling, then set back to default
		if (rollingAvg != 0.0f) 
			uiServices.setResults(Constants.THREAD_WRITE_UPLOAD_DATA,
					uploadMessage,
					ProcessIperf.formatFloatString(rollingAvg.toString()),
					false, false);
	}

	public void displayDownloadRollingAverage() {
		Float rollingAvg = 0.0f;
		Integer i = 0;
		rollingAvg = phase1DownloadResult + downloadSpeed; // phase1downloadResult
															// is 0 for phase 1
		try {
			for (i = 0; i < 4; i++) {
				if (!rollingDownloadDone[i]) {
					if (rollingDownloadCount[i] != 0) {
						rollingAvg += rollingDownloadSpeed[i]
								/ rollingDownloadCount[i];
					}
				}
			}
		} catch (Exception e) {
			if (Constants.DEBUG)
				System.out.println("In displayDownloadRollingAverage i ="
						+ i.toString());
		}
		if (isPhase2) {
			rollingAvg = rollingAvg / 2;
		} //rollingAvg *= 0.0009765625f;//gets multiplied while rolling, then set back to default 
		uiServices.setResults(Constants.THREAD_WRITE_DOWNLOAD_DATA,
				downloadMessage,
				ProcessIperf.formatFloatString(rollingAvg.toString()), false,
				false);

	}

	private void ParseTCPLine(String line, Integer threadNum) {

		Integer threadIndex = threadNum - Constants.IPERF_TCP_LOWEST_THREAD_NUM;
		if ((threadIndex < 0) || (threadIndex > 3)) {
			if (Constants.DEBUG)
				System.out.println("Bad thread index ="
						+ threadIndex.toString());
			return;
		}
		try {
			if (line.contains(" 0.0- 0.0")) { // ignore error line from iperf
				return;
			}
			switch (iperfThreadState[threadIndex]) {
			case 0:
				if (line.contains(" 0.0-")) {
					iperfThreadState[threadIndex] = 1;
					rollingUploadSpeed[threadIndex] += getTCPBitsPerSec(line);
					rollingUploadCount[threadIndex]++;
					displayUploadRollingAverage();
				}
				break;
			case 1:
				if (line.contains(" 0.0-")) {
					uploadSpeed += getTCPBitsPerSec(line);
					iperfThreadState[threadIndex] = 2;
					rollingUploadDone[threadIndex] = true;
					SetIperfUploadSpeedOnly();
				} else {
					if (!finishedUploadTest) {
						rollingUploadSpeed[threadIndex] += getTCPBitsPerSec(line);
						rollingUploadCount[threadIndex]++;
						displayUploadRollingAverage();
					}
				}
				break;
			case 2:
				if (line.contains(" 0.0-")) {
					rollingDownloadDone[threadIndex] = false; // reset done flag
																// for new
																// download
																// thread
					iperfThreadState[threadIndex] = 3;
					rollingDownloadSpeed[threadIndex] += getTCPBitsPerSec(line);
					rollingDownloadCount[threadIndex]++;
					displayDownloadRollingAverage();
				}
				break;
			case 3:
				if (line.contains(" 0.0-")) {
					downloadSpeed += getTCPBitsPerSec(line);
					iperfThreadState[threadIndex] = 2; // keep checking for
														// additional download
														// threads using this
														// thread number
					rollingDownloadDone[threadIndex] = true;
					displayDownloadRollingAverage();
				} else {
					rollingDownloadSpeed[threadIndex] += getTCPBitsPerSec(line);
					rollingDownloadCount[threadIndex]++;
					displayDownloadRollingAverage();
				}
				break;
			default:
				break;
			}
		} catch (Exception e) {
			if (Constants.DEBUG)
				System.out.println("Exception threadnum ="
						+ threadIndex.toString());
		}
	}

	public final void ProcessOutput(String lineout) {
		Integer startIndex = 0;
		Integer endIndex = 0;
		Integer threadNum = 0;
		String line;

		while (startIndex < lineout.length()) {
			endIndex = lineout.indexOf("\n", startIndex);
			line = lineout.substring(startIndex, endIndex);
			if (endIndex != -1) {
				if (isUDP) {
					ParseUDPLine(line);
				} else {
					threadNum = getThreadNum(line);
					if (threadNum != -1) {
						ParseTCPLine(line, threadNum);
					}
				}
				startIndex = endIndex + 1;
			} else {
				if (isUDP) {
					ParseUDPLine(line);
				} else {
					threadNum = getThreadNum(line);
					if (threadNum != -1) {
						ParseTCPLine(line, threadNum);
					}
				}
				startIndex = lineout.length();
			}
		}

	}

	public final void ProcessErrorOutput(String lineout) {
		Integer startIndex = 0;
		Integer endIndex = 0;
		String line;

		while (startIndex < lineout.length()) {
			endIndex = lineout.indexOf("\n", startIndex);
			if (endIndex == -1)
				endIndex = lineout.length();
			line = lineout.substring(startIndex, endIndex);

			if (isUDP) {
				if (line.matches(".*did not receive ack.*")
						|| line.matches(".*error:.*")) {
					this.udpMessage = "Delay Variation Incomplete";
				}
			} else {
				if (line.matches(".*failed.*") || line.matches(".*error:.*")) {
					downloadSuccess = false;
					downloadMessage = "Download Incomplete";
					if (!finishedUploadTest) {
						uploadSuccess = false;
						uploadMessage = "Upload Incomplete";
					}
				}
			}
			startIndex = endIndex + 1;
		}
		
		
	}
    /**
     * Calculate the MOS value
     * @return calculated MOS value
     */
    
    /**
     * Calculate R-Value from effective latency and mean udp packet loss
     * @return R-Value
     */
    public static double getRValue() {
	// EXCEL Function
        //=IF(AO2<160,(93.2-(AO2/40)-2.5*AN2),(93.2-(AO2-120)/10-2.5*AN2))

	// Translated
        // if (AO2 < 160) {
        //   return 93.2 - (AO2/40) - 2.5 * AN2;
        // } else {
        //   return 93.2 - (AO2 - 120) / 10 - 2.5 * AN2;
        // }
        
	// Further translate
        // if (Effective_Latency < 160) {
        //   return 93.2 - (Effective_Latency/40) - 2.5 * Mean_Packet_Loss;
        // } else {
        //   return 93.2 - (Effective_Latency - 120) / 10 - 2.5 * Mean_Packet_Loss;
        // }
        double effectiveLatency = getEffectiveLatency();
        double meanPacketLoss = getMeanUDPPacketLoss();
        double result;

        if (effectiveLatency < 160) {
            result = 93.2 - (effectiveLatency / 40) - 2.5 * meanPacketLoss;
            if (Globals.DEBUG_MOS) {
                System.out.println("MOS R-Value: " + result);
            }
        } else {
            result = 93.2 - (effectiveLatency - 120) / 10 - 2.5 * meanPacketLoss;
            if (Globals.DEBUG_MOS) {
                System.out.println("MOS R-Value: " + result);
            }
        }
        return result;
    }
    
    /**
     * Effective latency
     * @return effective latency
     */
    public static double getEffectiveLatency() {
	// EXCEL Function
        //=AL2+AM2*2+10
        
	// Translated
        // return Mean_Latency + Mean_Jitter * 2 + 10;
        double meanLatency = getMeanLatency();
        double result = meanLatency + meanLatency * 2 + 10;
        if (Globals.DEBUG_MOS) {
            System.out.println("MOS Effective Latency: " + result);
        }
        return result;
    }
    
    /**
     * Mean latency of ping packets
     * @return average of latency
     */
    public static double getMeanLatency() {
	// EXCEL Function
        //=AVERAGE(V2,Z2)

	// Translated
        // return (V2 + Z2) / 2;
        
	// Further translate
        // return (wPktAvg + ePktAvg) / 2;
        double result = average(pingAverages);
        if (Globals.DEBUG_MOS) {
            System.out.println("MOS Mean Latency: " + result);
        }
        return result;
    }
    
    private static ArrayList <Double> pingAverages;
    private static ArrayList <Double> udpJitters;
    private static ArrayList <Double> udpLosses;

    /**
     * Initialize all the variables
     * pingAverages - Pre-calculated ping average of each server test.
     * udpJitters - UDP Jitter from each server.
     * udpLosses - UDP Loss from each server. 
     */
    static {
        pingAverages = new ArrayList();
        udpJitters = new ArrayList();
        udpLosses = new ArrayList();
    }
    
    /**
     * reset the mos calculation data
     */
    public static void clearData() {
        pingAverages.clear();
        udpJitters.clear();
        udpLosses.clear();
    }
    
    /**
     * Mean udp packet loss (%)
     * @return average of udp packet loss
     */
    public static double getMeanUDPPacketLoss() {
	// EXCEL Function
        //=AVERAGE(AC2,AF2)

	// Translated
        // return (AC2 + AF2) / 2;
        
	// Further translate
        // return (wUDP_Loss + eUDP_Loss) / 2;
        double result = average(udpLosses);
        if (Globals.DEBUG_MOS) {
            System.out.println("MOS Mean Packet Loss: " + result);
        }
        return result;
    }
    
    /**
     * Returns the average of a list
     * @param numbers list of numbers
     * @return average of the list
     */
    private static double average(ArrayList <Double> numbers) {
        double sum = 0;
        for (Double number : numbers) {
            sum += number;
        }
        return sum / numbers.size();
    }

}
