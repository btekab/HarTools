package ar.com.yamamoto.hartools;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.wink.json4j.*;
import java.io.*;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

public class HarFile extends TextFile {
	private JSONObject json;
	private Character csvDelimiter;
	final private Character CSV_DEFAULT_DELIMITER = '\t';
	final private String timingNames[] = { "wait", "receive", "blocked", "send", "dns", "connect", "ssl" };
	final private Map<String, DescriptiveStatistics> statistics = new HashMap<String, DescriptiveStatistics>();
	final private DescriptiveStatistics totalStatistics = new DescriptiveStatistics();

	public HarFile(String filename, String charset)
			throws FileNotFoundException, IOException, JSONException, UnsupportedCharsetException {
		super(filename, charset);
		init();
	}
	
	public HarFile(String filename)
			throws FileNotFoundException, IOException, JSONException {
		super(filename);
		init();
	}
	
	/**
	 * Constructor helper
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws JSONException
	 */
	private void init()
			throws FileNotFoundException, IOException, JSONException {
		readFile();
		json = new JSONObject(getText());
		setDelimiter(CSV_DEFAULT_DELIMITER);
	}
	
	/**
	 * Crawl JSONObject and generate a CSV String for entries
	 * 
	 * @return
	 * 		CSV String
	 * @throws JSONException
	 */
	public String entriesToCsv()
			throws JSONException {
		StringBuilder csvBuffer = new StringBuilder();

		// CSV headers
		csvBuffer.append("url" + csvDelimiter
			+ "method" + csvDelimiter
			+ "startedDateTime" + csvDelimiter
			+ "time" + csvDelimiter
			+ "Response status" + csvDelimiter
			+ "Response content mimeType" + csvDelimiter
			+ "Response content size" + csvDelimiter
			+ "Response headersSize" + csvDelimiter
			+ "Response bodySize" + csvDelimiter
			+ "Referer" + csvDelimiter);
		
		for(int i = 0; i < timingNames.length; i++) {
			csvBuffer.append("Timing " + timingNames[i]);
			if(i + 1 < timingNames.length) {
				csvBuffer.append(csvDelimiter);
			}
		}
		
		csvBuffer.append(LINE_ENDING);
		
		JSONArray entries = ((JSONObject)json.get("log")).getJSONArray("entries");
		
		for(int i = 0; i < entries.length(); i++) {
			csvBuffer.append(singleEntryToCsvLine(entries.getJSONObject(i)));
			if(i < csvBuffer.length() - 1) {
				csvBuffer.append(LINE_ENDING);
			}
		}

		System.out.println("Statistics -> Total Timing : #" +  totalStatistics.getN() +
				"\n Min   = " + totalStatistics.getMin() +
				"\n Mean   = " + totalStatistics.getMean() +
				"\n STD    = " + totalStatistics.getStandardDeviation() +
				"\n Median = " + totalStatistics.getPercentile(50) +
				"\n 90%    = " + totalStatistics.getPercentile(90) +
				"\n 99%    = " + totalStatistics.getPercentile(99)  +
				"\n Max   = " + totalStatistics.getMax()
		);



		for (String timingName : timingNames) {

			DescriptiveStatistics descriptiveStatistics = statistics.get(timingName);


			System.out.println("Statistics -> " + timingName + " for : #" +  descriptiveStatistics.getN() +
					"\n Min   = " + descriptiveStatistics.getMin() +
					"\n Mean   = " + descriptiveStatistics.getMean() +
					"\n STD    = " + descriptiveStatistics.getStandardDeviation() +
					"\n Median = " + descriptiveStatistics.getPercentile(50) +
					"\n 90%    = " + descriptiveStatistics.getPercentile(90) +
					"\n 99%    = " + descriptiveStatistics.getPercentile(99)  +
					"\n Max   = " + descriptiveStatistics.getMax()
			);
		}


		return csvBuffer.toString();
	}
	
	/**
	 * Process a single entry
	 * 
	 * @param jsonObject
	 * @return
	 * 		CSV String line
	 * @throws JSONException
	 */
	private String singleEntryToCsvLine(JSONObject jsonObject)
			throws JSONException {
		StringBuilder csvLineBuffer = new StringBuilder();
		
		JSONObject requestObject = jsonObject.getJSONObject("request");
		JSONObject responseObject = jsonObject.getJSONObject("response");
		JSONObject contentObject = responseObject.getJSONObject("content");
		JSONObject timingsObject = jsonObject.getJSONObject("timings");

		// General keys
		csvLineBuffer.append(processKey(requestObject, "url") + csvDelimiter);
		csvLineBuffer.append(processKey(requestObject, "method") + csvDelimiter);
		csvLineBuffer.append(processKey(jsonObject, "startedDateTime") + csvDelimiter);

		csvLineBuffer.append(processKey(responseObject, "status") + csvDelimiter);
		csvLineBuffer.append(processKey(contentObject, "mimeType") + csvDelimiter);
		csvLineBuffer.append(processKey(contentObject, "size") + csvDelimiter);
		csvLineBuffer.append(processKey(responseObject, "headersSize") + csvDelimiter);
		csvLineBuffer.append(processKey(responseObject, "bodySize") + csvDelimiter);
		csvLineBuffer.append(processKey(
				requestObject.getJSONArray("headers"), "Referer") + csvDelimiter);


		//save stats for total time
		String timeValue = processKey(jsonObject, "time");
		if(null != timeValue && !timeValue.isEmpty()) {
			totalStatistics.addValue(Double.valueOf(timeValue));
		}
		csvLineBuffer.append(timeValue + csvDelimiter);

		// Timing keys
		for(int i = 0; i < timingNames.length; i++) {
			String value = processKey(timingsObject, timingNames[i]);

			csvLineBuffer.append(value);

			DescriptiveStatistics descriptiveStatistics = statistics.get(timingNames[i]);

			if(descriptiveStatistics ==null) {
				descriptiveStatistics = new DescriptiveStatistics();
				statistics.put(timingNames[i], descriptiveStatistics);
			}

			if(null != value && !value.isEmpty()) {
				descriptiveStatistics.addValue(Double.valueOf(value));
			}


			if(i + 1 < timingNames.length) {
				csvLineBuffer.append(csvDelimiter);
			}
		}
						
		return csvLineBuffer.toString();
	}

	/**
	 * Process key and return value as String
	 * (empty String, if not applicable or null)
	 * 
	 * @param object
	 * 		JSONObject
	 * @param key
	 * 		Name of the key
	 * @return
	 * 		Value of the key, or an empty String
	 * @throws JSONException
	 */
	private String processKey(JSONObject object, String key)
			throws JSONException {
		String returnValue = new String();
		
		if(object.has(key)) {
			if(!object.isNull(key)) {
				Object value = object.get(key);
				if(String.class.isInstance(value)) {
					if(value != null) {
						returnValue = (String)value;
					}
				} else if(Integer.class.isInstance(value)) {
					if((Integer)value >= 0) {
						returnValue = String.valueOf(value);
					}
				} else if(Double.class.isInstance(value)) {
					if((Double)value >= 0) {
						returnValue = String.valueOf(value);
					}
				}
			}
		}
		
		return returnValue;
	}
	
	/**
	 * Process key from JSONArray, and return value as String
	 * @param jsonArray
	 * 		JSONArray
	 * @param key
	 * 		Name of the JSONObject whose value we want to retrieve
	 * @return
	 * 		Value as String, or an empty string if the
	 * 		key was not found
	 * @throws JSONException
	 */
	private String processKey(JSONArray jsonArray, String key)
			throws JSONException {
		String returnValue = new String();
		
		for(Object entry : jsonArray.toArray()) {
			if(((JSONObject)entry).getString("name").equalsIgnoreCase(key)) {
				returnValue = processKey((JSONObject)entry, "value");
				break;
			}
		}
		
		return returnValue;
	}
	
	/**
	 * Set CSV delimiter
	 * 
	 * @param delimiter
	 */
	public void setDelimiter(Character delimiter) {
		if(delimiter != null)
			csvDelimiter = delimiter;
	}
}
