package org.luckyjourney.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-25 12:11
 */
@Configuration
public class EmailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.properties.mail.smtp.auth}")
    private boolean auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    private boolean starttlsEnable;

    @Bean
    public SimpleMailMessage simpleMailMessage(){
        return new SimpleMailMessage();
    }

    /**
     * 配置并创建 JavaMailSender 的方法
     *
     * @return 配置好的 JavaMailSender 对象，用于发送邮件
     */
    @Bean
    public JavaMailSender javaMailSender() {
        // 创建 JavaMailSenderImpl 实例
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        // 设置邮件服务器主机
        mailSender.setHost(host);
        // 设置邮件服务器端口
        mailSender.setPort(port);
        // 设置用于登录邮件服务器的用户名
        mailSender.setUsername(username);
        // 设置用于登录邮件服务器的密码
        mailSender.setPassword(password);
        // 创建邮件属性对象
        Properties properties = new Properties();
        // 设置邮件 SMTP 认证属性
        properties.setProperty("mail.smtp.auth", String.valueOf(auth));
        // 设置邮件 STARTTLS 启用属性
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
        // 为 JavaMailSenderImpl 设置邮件属性
        mailSender.setJavaMailProperties(properties);
        // 返回配置好的 JavaMailSender 对象
        return mailSender;
    }

}
