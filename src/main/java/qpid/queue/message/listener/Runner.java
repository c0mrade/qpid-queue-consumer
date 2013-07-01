package qpid.queue.message.listener;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Runner {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("appContext.xml");
	}
}
