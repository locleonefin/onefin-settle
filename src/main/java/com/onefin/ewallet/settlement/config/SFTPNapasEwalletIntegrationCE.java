package com.onefin.ewallet.settlement.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
public class SFTPNapasEwalletIntegrationCE {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPNapasEwalletIntegrationCE.class);

	@Autowired
	private ConfigLoader configLoader;

	@Bean
	public SessionFactory<LsEntry> sftpNapasSessionFactory() {
		LOGGER.info("== Init Napas Cashin Ecom SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpNapasHost());
				setPort(Integer.valueOf(configLoader.getSftpNapasPort()));
				setUser(configLoader.getSftpNapasUsername());
				setPassword(configLoader.getSftpNapasPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<LsEntry>(factory);
	}

	// Config download ftp

	@Bean(name = "SftpNapasRemoteFileTemplate")
	public SftpRemoteFileTemplate sftpNapasRemoteFileTemplate() {
		return new SftpRemoteFileTemplate(sftpNapasSessionFactory());
	}

	// Config upload ftp

	@Bean
	@ServiceActivator(inputChannel = "sftpOutNapasChannel")
	public MessageHandler handlerNapas() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpNapasSessionFactory());
		handler.setRemoteDirectoryExpression(new LiteralExpression(configLoader.getSftpNapasDirectoryRemoteOut()));
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
	public interface UploadNapasGateway {
		@Gateway(requestChannel = "sftpOutNapasChannel")
		void upload(File file);

	}
}
