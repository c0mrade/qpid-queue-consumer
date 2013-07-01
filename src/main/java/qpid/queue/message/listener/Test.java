package qpid.queue.message.listener;

import com.navteq.lcms.common.messaging.converter.JAXBMessageConverter;

public class Test {
	public static void main(String[] args) throws ClassNotFoundException {
		Executor exe = new Executor();
		Class<?> preda = Class.forName("com.navteq.lcms.common.view.place.Place");
		exe.setMessageConverter(new JAXBMessageConverter<preda>(preda));
	}
}
