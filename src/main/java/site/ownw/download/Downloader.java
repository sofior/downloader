package site.ownw.download;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author sofior
 * @date 2018/8/20 22:55
 */
@Slf4j
public class Downloader {

    private URL url;
    private int threadNumber;
    private File saveFile;
    private final String savePath;

    public Downloader(String url, String savePath, int threadNumber) throws MalformedURLException {
        if (threadNumber < 1) {
            throw new IllegalArgumentException("下载线程数不能小于1");
        }
        this.url = new URL(url);
        this.threadNumber = threadNumber;
        this.savePath = savePath;
    }

    public Downloader(URL url, int threadNumber) {
        this.savePath = null;
        if (threadNumber < 1) {
            throw new IllegalArgumentException("下载线程数不能小于1");
        }
        this.url = url;
        this.threadNumber = threadNumber;
    }

    public File download() throws IOException {
        List<DownloadFuture> tasks = new ArrayList<>(threadNumber);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("连接异常,状态码:" + connection.getResponseCode());
        }

        long total = connection.getContentLengthLong();
        log.info("文件总长:" + total + "字节");

        if (this.savePath != null) {
            this.saveFile = Paths.get(this.savePath).toFile();
        } else {
            saveFile = Files.createTempFile(UUID.randomUUID().toString().replaceAll("-", ""), ".tmp").toFile();
        }

        log.info("文件将存储到:{}", saveFile.getAbsolutePath());

        RandomAccessFile accessFile = new RandomAccessFile(saveFile, "rw");
        accessFile.setLength(total);
        accessFile.close();

        long blockSize = total / threadNumber;
        for (int i = 1; i < threadNumber; i++) {
            long endPos;
            long startPos = i * blockSize;
            if (i == threadNumber - 1) {
                endPos = total;
            } else {
                endPos = (i + 1) * blockSize - 1;
            }
            log.info("线程" + i + "下载的部分为：" + startPos + "-" + endPos);
            DownloadFuture downloadFuture = new DownloadFuture(saveFile, url, startPos, endPos, total);
            tasks.add(downloadFuture);
            downloadFuture.start();
        }
        log.info("主线程下载的部分为：" + 0 + "-" + (blockSize - 1));
        DownloadFuture downloadFuture = new DownloadFuture(saveFile, url, 0, blockSize - 1, total);
        tasks.add(downloadFuture);
        downloadFuture.run();
        while (true) {
            List<DownloadFuture> done = tasks.stream().filter(DownloadFuture::isDone).collect(Collectors.toList());
            if (done.size() == threadNumber) {
                List<DownloadFuture> errors = done.stream().filter(item -> item.getError() != null).collect(Collectors.toList());
                if (errors.size() != 0) {
                    StringBuilder builder = new StringBuilder(errors.get(0).getError());
                    for (int i = 1; i < errors.size(); i++) {
                        builder.append(",").append(errors.get(i).getError());
                    }
                    throw new IOException(builder.toString());
                } else {
                    break;
                }
            }
        }
        return saveFile;
    }

}
