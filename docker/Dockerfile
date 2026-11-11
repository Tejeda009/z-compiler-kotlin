FROM ubuntu:24.04

ARG TARGETARCH

RUN apt-get update \
    && apt-get install -y --no-install-recommends clang llvm ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY z-linux-${TARGETARCH} /usr/local/bin/z-compiler

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/z-compiler"]
