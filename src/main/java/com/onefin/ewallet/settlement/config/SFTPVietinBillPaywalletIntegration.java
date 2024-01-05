package com.onefin.ewallet.settlement.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.onefin.ewallet.settlement.service.ConfigLoader;

@Configuration
public class SFTPVietinBillPaywalletIntegration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPVietinBillPaywalletIntegration.class);

	@Autowired
	private ConfigLoader configLoader;

	@Bean
	public SessionFactory<LsEntry> sftpVietinSessionFactoryBillPay() {
		LOGGER.info("== Init Vietin BillPay SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpVietinbankBillPayHost());
				setPort(Integer.valueOf(configLoader.getSftpVietinbankBillPayPort()));
				setUser(configLoader.getSftpVietinbankBillPayUsername());
				setPassword(configLoader.getSftpVietinbankBillPayPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<LsEntry>(factory);
	}

	// Config download ftp
	@Bean(name = "SftpVietinBillPayRemoteFileTemplate")
	public SftpRemoteFileTemplate sftpVietinRemoteFileTemplate() {
		return new SftpRemoteFileTemplate(sftpVietinSessionFactoryBillPay());
	}

	// Config upload ftp
	@Bean
	@ServiceActivator(inputChannel = "sftpOutVietinBillPayChannel")
	public MessageHandler handlerVietinBillPay() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpVietinSessionFactoryBillPay());
		handler.setRemoteDirectoryExpression(
				new LiteralExpression(configLoader.getSftpVietinbankBillPayDirectoryRemoteOut()));
		handler.setFileNameGenerator(new FileNameGenerator() {
			@Override
			public String generateFileName(Message<?> message) {
				if (message.getPayload() instanceof File) {
					return ((File) message.getPayload()).getName();
				} else {
					throw new IllegalArgumentException("== File expected as payload.");
				}
			}
		});
		return handler;
	}

	@MessagingGateway
	public interface UploadVietinBillPayGateway {
		@Gateway(requestChannel = "sftpOutVietinBillPayChannel")
		void upload(File file);

	}
}
