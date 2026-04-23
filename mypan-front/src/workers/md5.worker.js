// src/workers/md5.worker.js
import SparkMD5 from "spark-md5";

self.onmessage = async (e) => {
  const { type, file, chunkSize } = e.data || {};
  if (type !== "START") return;

  try {
    const chunks = Math.ceil(file.size / chunkSize);
    const spark = new SparkMD5.ArrayBuffer();

    for (let i = 0; i < chunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(start + chunkSize, file.size);
      const buf = await file.slice(start, end).arrayBuffer();
      spark.append(buf);

      const pct = Math.floor(((i + 1) / chunks) * 100);
      self.postMessage({ type: "PROGRESS", progress: pct });
    }

    const md5 = spark.end();
    self.postMessage({ type: "DONE", md5 });
  } catch (err) {
    self.postMessage({ type: "ERROR", msg: err?.message || "MD5 failed" });
  }
};
