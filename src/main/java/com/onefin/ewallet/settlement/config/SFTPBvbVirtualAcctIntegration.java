package com.onefin.ewallet.settlement.config;


import com.jcraft.jsch.ChannelSftp;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.file.remote.synchronizer.InboundFileSynchronizer;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.io.File;

@Configuration
public class SFTPBvbVirtualAcctIntegration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SFTPBvbVirtualAcctIntegration.class);

	@Autowired
	private ConfigLoader configLoader;

	@Value("${sftp.bvbank.virtualAcct.directory.remoteIn}")
	private String sftpRemoteDirectory;

	@Bean
	public SessionFactory<ChannelSftp.LsEntry> sftpBvbSessionFactoryVirtualAcct() {
		LOGGER.info("== Init BVB VirtualAcct SFTP");
		DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
			{
				setHost(configLoader.getSftpBvbankVirtualAcctHost());
				setPort(Integer.parseInt(configLoader.getSftpBvbankVirtualAcctPort()));
				setUser(configLoader.getSftpBvbankVirtualAcctUsername());
				setPassword(configLoader.getSftpBvbankVirtualAcctPass());
				setAllowUnknownKeys(true);
			}
		};
		return new CachingSessionFactory<ChannelSftp.LsEntry>(factory);
	}

	// Config download ftp
	@Bean(name = "SftpBvbVirtualAcctRemoteFileTemplate")
	public SftpRemoteFileTemplate sftpBvbRemoteFileTemplate() {
		return new SftpRemoteFileTemplate(sftpBvbSessionFactoryVirtualAcct());
	}

	// Config upload ftp
	@Bean
	@ServiceActivator(inputChannel = "sftpOutBvbVirtualAcctChannel")
	public MessageHandler handlerBvbVirtualAcct() {
		SftpMessageHandler handler = new SftpMessageHandler(sftpBvbSessionFactoryVirtualAcct());
		handler.setRemoteDirectoryExpression(
				new LiteralExpression(configLoader.getSftpBvbankVirtualAcctDirectoryRemoteOut()));
		handler.setFileNameGenerator(new FileNameGenerator() {
			@Override
			public String generateFileName(Message<?> message) {
				try {
					return (String) message.getHeaders().get("filename");
				} catch (Exception e) {
					LOGGER.error("failed to generate file name", e);
					throw new IllegalArgumentException("== Expected header 'filename'");
				}
			}
		});
		return handler;
	}

	@MessagingGateway
	public interface UploadBvbVirtualAcctGateway {
		@Gateway(requestChannel = "sftpOutBvbVirtualAcctChannel")
		void upload(@Payload byte[] file,
					@Header("filename") String filename,
					@Header("path") String path);
	}


}
