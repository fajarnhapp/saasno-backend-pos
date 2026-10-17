# ==========================================
# STAGE 1: Build Stage (Menggunakan JDK + Maven)
# ==========================================
FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

# Install JDK 21 dan Curl untuk build
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jdk \
    curl \
    ca-certificates \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Salin source code project
COPY pom.xml .
COPY src ./src

# Jalankan build maven di dalam docker (Menghasilkan file .jar)
# Catatan: Jika ada Maven Wrapper (mvnw), silakan ganti perintah ke "./mvnw"
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && mvn clean package -DskipTests \
    && apt-get clean && rm -rf /var/lib/apt/lists/*


# ==========================================
# STAGE 2: Runtime Stage (Hanya JRE + Ubuntu 22.04 - Super Ringan)
# ==========================================
FROM ubuntu:22.04 AS runner

ENV TZ=Asia/Jakarta
ENV DEBIAN_FRONTEND=noninteractive

# Set timezone dan install JRE (bukan JDK penuh agar ringan) + utilitas dasar minimal
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        ca-certificates \
        curl \
        && apt-get clean && rm -rf /var/lib/apt/lists/*

# Set direktori kerja sesuai standard Anda
WORKDIR /home/application/data/apps/saasno-backend

# Ambil hasil .jar yang sudah jadi dari Stage 1 (builder)
COPY --from=builder /app/target/saasno-backend*.jar ./saasno-backend.jar

# Salin file konfigurasi logback & skrip runner internal
COPY src/main/resources/logback.xml ./logback.xml
COPY run.sh ./run.sh

RUN chmod +x run.sh

EXPOSE 80

ENTRYPOINT ["sh", "./run.sh"]