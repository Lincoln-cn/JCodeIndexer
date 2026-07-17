# Release 目录

存放构建产物，按版本号组织。

## 目录结构

```
release/
├── 1.0.0/
│   ├── jindexer.jar              # Fat JAR（通用，需 Java 21+）
│   ├── jindexer                  # Native Image（当前平台）
│   ├── checksums.sha256          # SHA-256 校验和
│   └── Dockerfile.release        # Docker 构建文件
├── 1.1.0/
│   └── ...
└── README.md
```

## 构建发布

```bash
# 基本构建（Fat JAR + Native Image）
./script/release.sh

# 指定版本号
./script/release.sh -v 1.0.0

# 构建并推送 Docker 镜像
DOCKER_REGISTRY=your-registry.com ./script/release.sh --push-docker

# 跳过 Native Image
./script/release.sh --skip-native
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VERSION` | 版本号（覆盖 --version） | 从 pom.xml 读取 |
| `DOCKER_REGISTRY` | Docker 镜像仓库 | `ghcr.io/sodlinken` |
| `DOCKER_IMAGE_NAME` | Docker 镜像名 | `java-code-indexer` |
| `DOCKER_TAG` | Docker 标签 | `latest` |

## 注意事项

- 产物文件（jar、binary、tar.gz）不提交到 git，仅保留在本地
- `release/README.md` 会提交到 git，作为目录说明
