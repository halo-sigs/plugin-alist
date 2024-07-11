# plugin-alist

AList 存储库插件，支持创建 AList 类型的存储库

## 使用方式

首先[部署一个 AList 服务](https://alist.nn.ci/zh/guide/install/docker.html),进入后台管理，创建存储库
![](docs/img/1.png)
根据文档填写相关信息，注意这里的挂载路径
![](docs/img/2.png)
安装并启用此插件后，在 Halo 后台新建存储策略
![](docs/img/3.png)
选择 AList 存储
![](docs/img/4.png)
根据提示填写以下信息
![](docs/img/5.png)
![](docs/img/6.png)
![](docs/img/7.png)
## 开发环境

插件开发的详细文档请查阅：<https://docs.halo.run/developer-guide/plugin/introduction>

所需环境：

1. Java 17
2. Node 18
3. pnpm 8
4. Docker (可选)

克隆项目：

```bash
git clone git@github.com:halo-sigs/plugin-alist.git

# 或者当你 fork 之后

git clone git@github.com:{your_github_id}/plugin-alist.git
```

```bash
cd path/to/plugin-alist
```

### 运行方式 1（推荐）

> 此方式需要本地安装 Docker

```bash
# macOS / Linux
./gradlew pnpmInstall

# Windows
./gradlew.bat pnpmInstall
```

```bash
# macOS / Linux
./gradlew haloServer

# Windows
./gradlew.bat haloServer
```

执行此命令后，会自动创建一个 Halo 的 Docker
容器并加载当前的插件，更多文档可查阅：<https://docs.halo.run/developer-guide/plugin/basics/devtools>

### 运行方式 2

> 此方式需要使用源码运行 Halo

编译插件：

```bash
# macOS / Linux
./gradlew build

# Windows
./gradlew.bat build
```

修改 Halo 配置文件：

```yaml
halo:
    plugin:
        runtime-mode: development
        fixedPluginPath:
            - "/path/to/plugin-alist"
```

最后重启 Halo 项目即可。
