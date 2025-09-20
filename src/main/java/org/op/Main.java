package org.op;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public String run(String[] args) throws Exception {
        int timeout = 200;
        String arg = args[0];
        List<String> addressList = switch (arg) {
            case "ip" -> {
                String s = Files.readString(new File(System.getProperty("user.dir") + "/op.txt").toPath());
                String decode = new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
                String[] split = StringUtils.split(decode, "\n");
                List<String> list = Arrays.stream(split).toList();

                System.out.printf("list : %d\n", list.size());

                yield list.stream()
                        .flatMap(sub -> Arrays.stream(new SubnetUtils(sub)
                                .getInfo()
                                .getAllAddresses()))
                        .toList();
            }
            default -> null;
        };

        if (addressList == null) {
            System.out.println("invalid args");
            return "";
        }

        int addressListSize = addressList.size();
        System.out.println("addr list size: " + addressListSize);

        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Result>> futureList = addressList.stream()
                .map(address -> executorService.submit(new Connect(address, 443, timeout, arg)))
                .toList();

        Semaphore semaphore = new Semaphore(Runtime.getRuntime().availableProcessors());
        AtomicLong processed = new AtomicLong();
        processed.set(0L);
        List<Result> resultList = futureList.stream()
                .map(resultFuture -> {
                    try {
                        semaphore.acquire();
                        Result result = resultFuture.get();
                        semaphore.release();

                        long processedSize = processed.get();
                        processed.set(processedSize + 1);

                        return result;
                    } catch (InterruptedException | ExecutionException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Result::getMillisecond))
                .toList();
        executorService.close();

        System.out.println("resultList size: " + resultList.size());

        StringBuilder builder = new StringBuilder();
        resultList.stream().limit(10).forEach(t -> builder.append(t.getAddress()).append("\n"));
        return StringUtils.trim(builder.toString());
    }

}