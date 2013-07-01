package qpid.queue.message.listener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.url.AMQBindingURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.support.converter.MessageConverter;

public class Executor {

	private static final Logger LOG = LoggerFactory.getLogger(Executor.class);

	private MessageConverter messageConverter;
	private MessageConverter messageConverterWithValidation;
	private BufferedWriter wr;
	private boolean saveResults;
	private SimpleDateFormat df;
	private File resultDir;
	private File resultFile;
	private File errorDir;
	private JAXBContext jc;
	private String errorDirectory;
	private String connectionURL;
	private String bindingURL;
	private File counterFile;

	public void init() throws IOException, IllegalAccessException {
		df = new SimpleDateFormat("yyyy-MM-dd h:mm:ss");
		resultFile = new File("result.csv");
		counterFile = new File("counters.txt");

		if (resultFile.exists()) {
			resultFile.delete();
		}

		if (counterFile.exists()) {
			counterFile.delete();
		}

		boolean isSuccess = (resultFile.createNewFile() && counterFile
				.createNewFile());
		if (!isSuccess) {
			throw new IllegalAccessException(
					"Please check your permissions, can't create temporary file");
		}

		wr = new BufferedWriter(new FileWriter(resultFile));

		wr.write("User Name|First Name|Last Name|Message sample property");
		wr.write("\n");
		wr.close();

		if (saveResults) {
			try {
				jc = JAXBContext.newInstance(SampleObject.class);
			} catch (JAXBException e) {
				e.printStackTrace();
			}

			resultDir = new File("results");
			errorDir = new File("error");

			if (!resultDir.exists()) {
				boolean success = resultDir.mkdir();

				if (!success) {
					throw new IllegalAccessException(
							"Please check your permissions, can't create temporary file");
				}
			} else {
				// delete old results
				for (File file : resultDir.listFiles()) {
					file.delete();
				}
			}

			errorDirectory = resultDir.getAbsolutePath() + File.separator
					+ errorDir;

			if (!new File(errorDirectory).exists()) {
				boolean success = new File(errorDirectory).mkdir();

				if (!success) {
					throw new IllegalAccessException(
							"Please check your permissions, can't create temporary file");
				}
			} else {
				for (File file : new File(errorDirectory).listFiles()) {
					file.delete();
				}
			}
		}

		fetch();
	}

	private void fetch() throws IOException {
		Enumeration e = null;
		int numMsgs = 0;
		Queue queue = null;
		QueueConnection queueConn = null;

		Map<String, Integer> countryCodeMap = new HashMap<String, Integer>();

		try {
			AMQBindingURL burl = new AMQBindingURL(bindingURL);

			// create the queue object
			queue = new AMQQueue(burl);

			// create queue connection factory
			QueueConnectionFactory connFactory = new AMQConnectionFactory(
					connectionURL);

			// create a queue connection
			queueConn = connFactory.createQueueConnection();

			// create a queue session
			QueueSession queueSession = queueConn.createQueueSession(true,
					Session.CLIENT_ACKNOWLEDGE);

			// create a queue browser
			QueueBrowser queueBrowser = queueSession.createBrowser(queue);

			// start the connection
			queueConn.start();

			// browse the messages
			e = queueBrowser.getEnumeration();
		} catch (Exception exe) {
			exe.printStackTrace();
		}

		// count number of messages
		while (e.hasMoreElements()) {
			Message msg = (Message) e.nextElement();
			SampleObject obj = null;
			String validation = "Pass";
			wr = new BufferedWriter(new FileWriter(resultFile, true));

			try {
				if (msg instanceof BytesMessage) {

					// try-catch block added because published dlq message
					// should
					// have different error code

					String date = df.format(new Date(msg.getJMSTimestamp()));

					// country counter
					int countryCount;

					try {
						obj = (SampleObject) messageConverterWithValidation
								.fromMessage(msg);
					} catch (Exception ex) {
						ex.printStackTrace();
						String errorMsg = new StringBuilder()
								.append(
										"Unable to deserialize message to object, message: ")
								.append(ex.getMessage()).toString();
						LOG.error(errorMsg);
						validation = ex.getMessage();
					}

					if (!validation.equals("Pass")) {
						obj = (SampleObject) messageConverter.fromMessage(msg);
					}

					String userName = obj.getUserName();
					String firstName = obj.getFirstName();
					String lastName = obj.getLastName();

					// This is from message header can be anything
					String sampleMessageProperty = msg
							.getStringProperty("objUpdateTimestamp");

					wr.write(userName + "|" + firstName + "|" + lastName + "|"
							+ sampleMessageProperty.trim());
					wr.write("\n");

					if (saveResults) {
						Marshaller m = jc.createMarshaller();
						m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
								Boolean.TRUE);
						if (validation.equals("Pass")) {
							OutputStream os = new FileOutputStream(new File(
									resultDir.getAbsolutePath()
											+ File.separator + firstName + "_"
											+ lastName + ".xml"));
							m.marshal(obj, os);
						} else {
							OutputStream os = new FileOutputStream(new File(
									errorDirectory + File.separator + firstName
											+ "_" + lastName + ".xml"));
							m.marshal(obj, os);
						}
					}

					wr.close();

				} else {
					String errorMsg = "Unable to recognize message, message should to be instance of javax.jms.BytesMessage.";
					LOG.error(errorMsg);
				}
				numMsgs++;
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		System.out.println(queue + " has " + numMsgs + " messages");

		// close the queue connection
		try {
			queueConn.close();
		} catch (JMSException e1) {
			e1.printStackTrace();
		}

	}

	public void destroy() {
		System.out.println("destroy");
	}

	public MessageConverter getMessageConverter() {
		return messageConverter;
	}

	public void setMessageConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	public MessageConverter getMessageConverterWithValidation() {
		return messageConverterWithValidation;
	}

	public void setMessageConverterWithValidation(
			MessageConverter messageConverterWithValidation) {
		this.messageConverterWithValidation = messageConverterWithValidation;
	}

	public boolean isSaveResults() {
		return saveResults;
	}

	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}

	public String getConnectionURL() {
		return connectionURL;
	}

	public void setConnectionURL(String connectionURL) {
		this.connectionURL = connectionURL;
	}

	public String getBindingURL() {
		return bindingURL;
	}

	public void setBindingURL(String bindingURL) {
		this.bindingURL = bindingURL;
	}

}
