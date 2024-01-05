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
public class SFTPVietinVirtualAcctIntegration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPVietinVirtualAcctIntegration.class);

	@Autowired
	private ConfigLoader configLoader;

	@Bean
	public SessionFactory<LsEntry> sftpVietinSessionFactoryVirtualAcct() {
		LOGGER.info("== Init Vietin VirtualAcct SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpVietinbankVirtualAcctHost());
				setPort(Integer.parseInt(configLoader.getSftpVietinbankVirtualAcctPort()));
				setUser(configLoader.getSftpVietinbankVirtualAcctUsername());
				setPassword(configLoader.getSftpVietinbankVirtualAcctPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<LsEntry>(factory);
	}

	// Config download ftp
	@Bean(name = "SftpVietinVirtualAcctRemoteFileTemplate")
	public SftpRemoteFileTemplate sftpVietinRemoteFileTemplate() {
		return new SftpRemoteFileTemplate(sftpVietinSessionFactoryVirtualAcct());
	}

	// Config upload ftp

	@Bean
	@ServiceActivator(inputChannel = "sftpOutVietinVirtualAcctChannel")
	public MessageHandler handlerVietinVirtualAcct() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpVietinSessionFactoryVirtualAcct());
		handler.setRemoteDirectoryExpression(
				new LiteralExpression(configLoader.getSftpVietinbankVirtualAcctDirectoryRemoteOut()));
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
	public interface UploadVietinVirtualAcctGateway {
		@Gateway(requestChannel = "sftpOutVietinVirtualAcctChannel")
		void upload(File file);

	}
}
