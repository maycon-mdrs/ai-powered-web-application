# Ambiente AWS — TP3 (conta pessoal + Docker Compose)

Guia passo a passo: **1 EC2** sobe a stack Spring via **Docker Compose**; o **MS3** é **AWS Lambda** (Function URL). Conta **pessoal** (não Academy).

Preencha a tabela ao criar os recursos. Kill de réplica = `docker stop` de um container (não precisa de 2ª EC2).

---

## 0. Arquitetura

```text
PC (JMeter / Postman)
   │  :8080  (ou túnel SSH)
   ▼
┌──────────────── EC2 + Elastic IP (Docker Compose) ────────────────┐
│ eureka1/2 · config-server · api-gateway                            │
│ ms-coordenador-a / ms-coordenador-b · ms-ia                        │
│ vault (profile) · prometheus · grafana · zipkin                    │
│ (opcional) ms-lambda local — só se não usar Function URL           │
└────────────────────────────┬───────────────────────────────────────┘
                             │ HTTPS
                             ▼
                    AWS Lambda Function URL
```

**Escopo:** plataforma Spring Cloud, 3 MSs (coord + IA + Lambda), resiliência, obs, JMeter, Vault, Memory+RAG, MCP terceiro.  
**Fora:** MCP próprio, UI, multi-EC2.

---

## 1. Tabela do ambiente

| Campo | Valor |
| ----- | ----- |
| Conta | AWS pessoal |
| Região | `us-east-2` (Ohio) |
| Nome EC2 | `UNIDADE 3` |
| AMI | Amazon Linux 2023 |
| Tipo | `t3.large` (confirme no console) |
| Disco | 30 GB gp3 (confirme no console) |
| Usuário SSH | `ec2-user` |
| Key pair | `my-key` → `my-key.pem` (api-gateway / caminho local) |
| Security Group | `launch-wizard-2` (confirme portas 8080, 8761, …) |
| Elastic IP | `77.114.59.85` |
| IP privado | `172.31.35.37` |
| Instance ID | `______` |
| Lambda name | `plano-leitura` |
| Function URL | `https://lvedwgvp7nb2srqvltwos37y7u0vztzp.lambda-url.us-east-2.on.aws/` |
| IAM role Lambda | `tp3-plano-leitura-role` |

---

## 2. Credenciais no PC

```powershell
aws configure
# Access Key, Secret, região (ex.: us-east-1)
aws sts get-caller-identity
```

---

## 3. Criar a EC2

### 3.1 Key pair

```powershell
aws ec2 create-key-pair --key-name tp3-key --query KeyMaterial --output text > tp3-key.pem
$k = "C:\caminho\tp3-key.pem"
icacls.exe $k /reset
icacls.exe $k /GRANT:R "$($env:USERNAME):R"
icacls.exe $k /inheritance:r
```

### 3.2 Security Group

| Porta | Uso | Origem |
| ----- | --- | ------ |
| 22/TCP | SSH | seu IP |
| 8080/TCP | API Gateway | seu IP (ou `0.0.0.0/0` se necessário na apresentação) |
| 8761/TCP | Eureka UI | seu IP |
| 8762/TCP | Eureka peer 2 | seu IP |
| 8082/TCP | GraphiQL | seu IP |
| 8888/TCP | Config Server | seu IP |
| 3000/TCP | Grafana | seu IP |
| 9090/TCP | Prometheus | seu IP |
| 9411/TCP | Zipkin | seu IP |
| 8200/TCP | Vault (só se demo de fora) | seu IP |

```powershell
$MEU_IP = (Invoke-RestMethod https://checkip.amazonaws.com).Trim() + "/32"
$VPC = aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query "Vpcs[0].VpcId" --output text
$SG = aws ec2 create-security-group --group-name tp3-sg --description "TP3" --vpc-id $VPC --query GroupId --output text
foreach ($p in 22,8080,8761,8762,3000,9411,9090,8082,8888,8200) {
  aws ec2 authorize-security-group-ingress --group-id $SG --protocol tcp --port $p --cidr $MEU_IP
}
```

### 3.3 Instância + Elastic IP

```powershell
$AMI = aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 --query Parameter.Value --output text
$IID = aws ec2 run-instances --image-id $AMI --instance-type t3.large --key-name tp3-key `
  --security-group-ids $SG `
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' `
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=tp3-assistente}]' `
  --query "Instances[0].InstanceId" --output text

# Espere status running, depois:
$ALLOC = aws ec2 allocate-address --domain vpc --query AllocationId --output text
aws ec2 associate-address --instance-id $IID --allocation-id $ALLOC
aws ec2 describe-addresses --allocation-ids $ALLOC --query "Addresses[0].PublicIp" --output text
```

Anote o Elastic IP na tabela. Sem EIP, o IP público muda a cada stop/start.

### 3.4 SSH

```powershell
ssh -i C:\caminho\tp3-key.pem ec2-user@<ELASTIC_IP>
```

---

## 4. Docker na EC2

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# saia e entre de novo no SSH

sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

Swap (recomendado):

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=4096
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Clone:

```bash
cd ~
git clone <URL_DO_REPO> ai-powered-web-application
cd ai-powered-web-application
cp .env.example .env
```

---

## 5. Lambda (MS3) — conta pessoal

Handler: `br.imd.ufrn.lambda.PlanoLeituraUrlHandler::handleRequest`  
Artifacts (após `mvn -pl ms-lambda -am package`):
- `ms-lambda-0.0.1-SNAPSHOT-aws.jar` — upload na Lambda (classes na raiz)
- `ms-lambda-0.0.1-SNAPSHOT-boot.jar` — app Spring local / Compose `:9000`

### 5.1 IAM role

```powershell
# trust
@'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "lambda.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
'@ | Set-Content -Encoding ascii trust-lambda.json

aws iam create-role --role-name tp3-plano-leitura-role --assume-role-policy-document file://trust-lambda.json
aws iam attach-role-policy --role-name tp3-plano-leitura-role `
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

$ACCOUNT = aws sts get-caller-identity --query Account --output text
$ROLE_ARN = "arn:aws:iam::${ACCOUNT}:role/tp3-plano-leitura-role"
# aguarde ~10s a propagação da role
```

### 5.2 Criar função + Function URL

No PC (com o JAR gerado):

```powershell
cd /caminho/do/ai-powered-web-application
mvn -q -DskipTests package -pl ms-lambda -am

aws lambda create-function `
  --function-name plano-leitura `
  --runtime java21 `
  --handler "br.imd.ufrn.lambda.PlanoLeituraUrlHandler::handleRequest" `
  --role $ROLE_ARN `
  --memory-size 512 --timeout 15 `
  --zip-file fileb://ms-lambda/target/ms-lambda-0.0.1-SNAPSHOT-aws.jar

aws lambda create-function-url-config --function-name plano-leitura --auth-type NONE
aws lambda add-permission --function-name plano-leitura `
  --statement-id public-url --action lambda:InvokeFunctionUrl `
  --principal "*" --function-url-auth-type NONE

aws lambda get-function-url-config --function-name plano-leitura --query FunctionUrl --output text
```

Teste:

```powershell
curl.exe -X POST "<FUNCTION_URL>plano" -H "Content-Type: application/json" `
  -d "{\"titulo\":\"Neuromancer\",\"autor\":\"William Gibson\",\"paginasEstimadas\":271,\"diasDisponiveis\":14}"
```

Atualizar código:

```powershell
aws lambda update-function-code --function-name plano-leitura `
  --zip-file fileb://ms-lambda/target/ms-lambda-0.0.1-SNAPSHOT-aws.jar
```

---

## 6. Subir a stack na EC2 (Compose) — modo deploy

Sem stub: LLM real (Vault) + MCP Open Library + Lambda AWS.

```bash
cd ~/ai-powered-web-application
git pull
cp .env.deploy.example .env
# confira LAMBDA_BASE_URL da Function URL
chmod +x scripts/*.sh
./scripts/docker-up-deploy.sh
```

Grave a chave no Vault (não versionar em Git):

```bash
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root vault \
  vault kv put secret/ms-ia GEMINI_API_KEY=SUA_CHAVE OPENAI_API_KEY=nao-usado
```

Smoke **só stub** (sem chave), se precisar:

```bash
cp .env.example .env
# LAMBDA_BASE_URL=Function URL
./scripts/docker-up.sh none
```

Conferência:

```bash
docker ps
curl -s http://127.0.0.1:8761/ | head
curl -s -X POST http://127.0.0.1:8080/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"demo-1","message":"Quero ficcao cientifica curta"}'
```

Plano (Lambda AWS ou local) — endpoint separado:

```bash
curl -s -X POST http://127.0.0.1:8080/api/assistant/plano -H 'Content-Type: application/json' \
  -d '{"titulo":"Neuromancer","autor":"William Gibson","paginasEstimadas":271,"diasDisponiveis":14}'
```

---

## 7. Vault + LLM real (demo)

```bash
# sobe Vault + stack; ms-ia com perfis docker,vault
export COMPOSE_PROFILES=vault
# em .env:
# MS_IA_PROFILES=docker,vault
# AI_STUB=false
# VAULT_TOKEN=root
# VAULT_URI=http://vault:8200

docker compose --profile vault --profile local-lambda up -d --build
# ou reinicie só o ms-ia após gravar o secret:

docker exec -it vault sh -c 'export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=root; vault kv put secret/ms-ia GEMINI_API_KEY=SUA_CHAVE'
docker compose up -d --force-recreate ms-ia
```

Vault `-dev` perde dados ao reiniciar o container.

---

## 8. Config dinâmica (Git)

No `.env` do host (ver `.env.deploy.example`):

```env
CONFIG_PROFILE=git
CONFIG_GIT_URI=https://github.com/maycon-mdrs/ai-powered-web-application.git
CONFIG_GIT_SEARCH_PATHS=config-repo
CONFIG_GIT_FORCE_PULL=true
```

Recreate: `docker compose up -d --build --force-recreate config-server`

Fluxo de alteração em runtime:

1. Alterar `config-repo/ms-ia.yml` no repositório → `git commit` → `git push`
2. `curl -X POST http://<EIP>:8082/actuator/refresh`
3. Validar no GraphiQL ou no endpoint de chat

Com `CONFIG_PROFILE=native`, o Config Server lê apenas o volume montado no host do Compose.

---

## 9. Kill / Chaos / JMeter

```bash
# Kill réplica (Gateway/Eureka)
docker stop ms-coordenador-b
# religar
docker start ms-coordenador-b

# Chaos Monkey
curl -X POST http://127.0.0.1:8081/actuator/chaosmonkey/enable
curl -X POST http://127.0.0.1:8083/actuator/chaosmonkey/enable
# disable idem
```

JMeter no PC:

```powershell
jmeter -n -t jmeter\assistant-chat.jmx -JgatewayHost=<EIP> -l jmeter\resultados\run1.jtl
```

Túnel se a rede do PC falhar:

```powershell
ssh -i C:\caminho\tp3-key.pem -L 18080:127.0.0.1:8080 -N ec2-user@<EIP>
# gatewayHost=127.0.0.1 porta 18080
```

Carga: `AI_STUB=true`, `MCP_ENABLED=false`, threads > 5 e &lt; knee, **0 erros**.

---

## 10. Painéis

Guia detalhado (o que abrir e o que mostrar): [como-visualizar.md](como-visualizar.md).

| URL | Uso |
| --- | --- |
| `http://<EIP>:8080` | Gateway |
| `http://<EIP>:8761` | Eureka cluster |
| `http://<EIP>:8082/graphiql` | GraphQL |
| `http://<EIP>:3000` | Grafana (admin/admin) → Prometheus `http://prometheus:9090` |
| `http://<EIP>:9411` | Zipkin |
| `http://<EIP>:9090` | Prometheus |

---

## 11. Parar / reiniciar

```bash
cd ~/ai-powered-web-application
docker compose --profile local-lambda --profile vault down
# Start da EC2 não sobe sozinho — rode de novo docker-up / compose up
```

---

## Checklist final

- [ ] `aws sts get-caller-identity` OK (conta pessoal)
- [ ] EC2 `t3.large` + **Elastic IP** anotado
- [ ] SG com portas necessárias
- [ ] Docker + Compose na VM
- [ ] Role IAM + Lambda + Function URL testada com `curl`
- [ ] `.env` com `LAMBDA_BASE_URL` da Function URL
- [ ] `docker compose up` — Eureka com 2× `MS-COORDENADOR`, `MS-IA`, `API-GATEWAY`
- [ ] Chat 200 em `/api/assistant/chat`; plano 200 em `/api/assistant/plano` (Lambda AWS)
- [ ] Kill `ms-coordenador-b` + Chaos on/off
- [ ] Grafana/Zipkin + JMeter 0 erros (&lt; knee)
- [ ] (Demo) Vault + refresh Config + MCP uma vez
