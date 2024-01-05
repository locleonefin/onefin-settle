package com.onefin.ewallet.settlement.config;

import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.onefin.ewallet.settlement.service.ConfigLoader;
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

import java.io.File;

@Configuration
public class SFTPVietinDisbursementIntegration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPVietinDisbursementIntegration.class);

	@Autowired
	private ConfigLoader configLoader;

	@Bean
	public SessionFactory<LsEntry> sftpVietinSessionFactoryDisbursement() {
		LOGGER.info("== Init Vietin Disbursement SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpVietinbankDisbursementHost());
				setPort(Integer.valueOf(configLoader.getSftpVietinbankDisbursementPort()));
				setUser(configLoader.getSftpVietinbankDisbursementUsername());
				setPassword(configLoader.getSftpVietinbankDisbursementPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<LsEntry>(factory);
	}

	// Config download ftp
	@Bean(name = "SftpVietinDisbursementRemoteFileTemplate")
	public SftpRemoteFileTemplate sftpVietinRemoteFileTemplate() {
		return new SftpRemoteFileTemplate(sftpVietinSessionFactoryDisbursement());
	}

	// Config upload ftp

	@Bean
	@ServiceActivator(inputChannel = "sftpOutVietinDisbursementChannel")
	public MessageHandler handlerVietinDisbursement() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpVietinSessionFactoryDisbursement());
		handler.setRemoteDirectoryExpression(new LiteralExpression(configLoader.getSftpVietinbankDisbursementDirectoryRemoteOut()));
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
	public interface UploadVietinDisbursementGateway {
		@Gateway(requestChannel = "sftpOutVietinDisbursementChannel")
		void upload(File file);

	}
}
