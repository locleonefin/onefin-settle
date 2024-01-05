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
public class SFTPNapasEwalletIntegrationCO {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPNapasEwalletIntegrationCO.class);

	@Autowired
	private ConfigLoader configLoader;

	@Bean
	public SessionFactory<LsEntry> sftpNapasSessionFactoryCO() {
		LOGGER.info("== Init Napas Cashout Ecom SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpCONapasHost());
				setPort(Integer.valueOf(configLoader.getSftpCONapasPort()));
				setUser(configLoader.getSftpCONapasUsername());
				setPassword(configLoader.getSftpCONapasPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<LsEntry>(factory);
	}

	// Config download ftp

	@Bean(name = "SftpNapasRemoteFileTemplateCO")
	public SftpRemoteFileTemplate sftpNapasRemoteFileTemplateCO() {
		return new SftpRemoteFileTemplate(sftpNapasSessionFactoryCO());
	}

	// Config upload ftp

	@Bean
	@ServiceActivator(inputChannel = "sftpOutNapasChannelCO")
	public MessageHandler handlerNapasCO() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpNapasSessionFactoryCO());
		handler.setRemoteDirectoryExpression(new LiteralExpression(configLoader.getSftpCONapasDirectoryRemoteOut()));
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
	public interface UploadNapasGatewayCO {
		@Gateway(requestChannel = "sftpOutNapasChannelCO")
		void upload(File file);

	}
}
