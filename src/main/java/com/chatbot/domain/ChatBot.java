package com.chatbot.domain;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chatbot.event.WebSocketEventSourceListener;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.completions.Completion;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import okhttp3.sse.EventSourceListener;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedList;
import java.util.List;

@ApplicationScoped
public class ChatBot {

    public static final String HTTPS_API_OPENAI_COM = "https://api.openai.com/";
    public static final String CHAT_HISTORY_PREFIX = "chatHistory:";

    @ConfigProperty(name = "proxy.enable", defaultValue = "false")
    Boolean enableProxy;

    @ConfigProperty(name = "proxy.host", defaultValue = "localhost")
    String proxyHost;
    @ConfigProperty(name = "proxy.port", defaultValue = "7654")
    Integer proxyPort;

    @Inject
    ReactiveRedisDataSource redisReactive;

    public Uni<String> chat(String apiKey, String openId, String prompt, EventSourceListener eventSourceListener) {
        ReactiveValueCommands<String, String> value = redisReactive.value(String.class);
        List<Message> messages = ListUtil.list(true);
        String redisKey = CHAT_HISTORY_PREFIX + openId;
        return value.get(redisKey)
                .invoke(history -> {
                    if (StrUtil.isBlank(history)) {
                        messages.add(Message.builder().role(Message.Role.SYSTEM).content("你是一个可以问答任何问题的全能机器人").build());
                    } else {
                        messages.addAll(JSONUtil.toList(history, Message.class));
                        messages.add(Message.builder().role(Message.Role.USER).content(prompt).build());
                    }
                    ChatCompletion chatCompletion = ChatCompletion.builder().messages(messages).build();
                    ((WebSocketEventSourceListener) eventSourceListener).saveResponse(response -> {
                        messages.add(Message.builder().role(Message.Role.ASSISTANT).content(response).build());
                        while (messages.size() > 10) {
                            ((LinkedList<Message>) messages).removeFirst();
                        }
                        value.set(redisKey, JSONUtil.toJsonStr(messages)).await().indefinitely();
                    });
                    getClient(apiKey).streamChatCompletion(chatCompletion, eventSourceListener);
                });
    }

    public void completions(String apiKey, String prompt, EventSourceListener eventSourceListener) {
        Completion q = Completion.builder()
                .prompt(prompt)
                .stream(true)
                .build();
        getClient(apiKey).streamCompletions(q, eventSourceListener);
    }


    private OpenAiStreamClient getClient(String apikey) {
        OpenAiStreamClient.Builder builder = OpenAiStreamClient.builder()
                .connectTimeout(50)
                .readTimeout(50)
                .writeTimeout(50)
                .apiKey(apikey)
                .apiHost(HTTPS_API_OPENAI_COM);
        if (enableProxy) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            builder.proxy(proxy);
        }
        return builder.build();
    }
}