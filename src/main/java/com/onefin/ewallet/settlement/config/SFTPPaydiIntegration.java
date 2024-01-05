package com.onefin.ewallet.settlement.config;

import com.jcraft.jsch.ChannelSftp.LsEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
public class SFTPPaydiIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SFTPPaydiIntegration.class);

    @Autowired
    private Environment env;

    @Bean
    public SessionFactory<LsEntry> sftpPaydiSessionFactory() {
        LOGGER.info("== Init Paydi SFTP");
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true) {
            {
                setHost(env.getProperty("sftp.merchant.paydi.host"));
                setPort(Integer.valueOf(env.getProperty("sftp.merchant.paydi.port")));
                setUser(env.getProperty("sftp.merchant.paydi.username"));
                setPassword(env.getProperty("sftp.merchant.paydi.password"));
                setAllowUnknownKeys(true);
            }
        };
        return new CachingSessionFactory<LsEntry>(factory);
    }

    // Config download ftp
    @Bean(name = "SftpPaydiRemoteFileTemplate")
    public SftpRemoteFileTemplate sftpPaydiRemoteFileTemplate() {
        return new SftpRemoteFileTemplate(sftpPaydiSessionFactory());
    }

    // Config upload ftp
    @Bean
    @ServiceActivator(inputChannel = "sftpOutPaydiChannel")
    public MessageHandler handlerPaydi() {
        SftpMessageHandler handler = new SftpMessageHandler(sftpPaydiSessionFactory());
        handler.setRemoteDirectoryExpression(new LiteralExpression(env.getProperty("sftp.merchant.paydi.directory.remoteOut")));
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
    public interface UploadPaydiGateway {
        @Gateway(requestChannel = "sftpOutPaydiChannel")
        void upload(File file);

    }
}
