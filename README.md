# Messager

App Android simples de mensagens entre dois (ou mais) celulares da família.
Funciona só com Wi‑Fi (não precisa de chip), **não precisa de conta Google
nem de WhatsApp** e avisa com **som e vibração** quando chega mensagem e o
app não está aberto na tela.

## Como funciona

- Os celulares se encontram por um **código da família** (ex.: `K7QM-4XHP-R2TA-9BNC`).
  Os dois precisam usar o mesmo código.
- As mensagens passam pelo servidor público gratuito [ntfy.sh](https://ntfy.sh),
  mas vão **criptografadas (AES‑256)** com uma chave tirada do código: o servidor
  não consegue ler o conteúdo.
- Um serviço em segundo plano mantém a conexão aberta, por isso o aviso chega
  na hora mesmo com a tela apagada. Aparece uma notificação fixa discreta
  "Messager pronto para receber" — é ela que mantém o app vivo.
- Se um celular ficar sem internet, ao voltar ele recebe o que chegou nas
  últimas ~12 horas.
- Botão **🔔 Chamar**: faz o outro celular tocar e vibrar forte, para chamar atenção.
- O histórico fica guardado só nos celulares.

## Instalar

1. No celular, abra a página **Releases** deste repositório no GitHub e baixe o
   arquivo `Messager.apk` (também fica em *Actions → último build → Artifacts*).
2. Abra o arquivo. O Android vai pedir para **permitir instalar apps desta fonte**
   (Chrome/Arquivos) — permita e instale.
3. Faça o mesmo no outro celular.

## Configurar

1. No **primeiro celular**: digite seu nome (ex.: "Pai"), toque em
   **Gerar código novo**, anote o código e toque em **Salvar e começar**.
2. No **celular do seu filho**: digite o nome dele e **o mesmo código**.
3. Nos dois: aceite a permissão de **notificações** e toque no aviso amarelo
   para **liberar da economia de bateria** (escolha "Permitir").
   Sem isso o Android pode pausar o app e os avisos atrasam.

### Dicas para não perder avisos

- Samsung: *Configurações → Bateria → Limites de uso em segundo plano* →
  coloque o Messager em **Apps que nunca suspendem**.
- Xiaomi/Redmi: nas informações do app, ative **Início automático** e em
  *Economia de bateria* escolha **Sem restrições**.
- Motorola/outros: *Configurações → Apps → Messager → Bateria* → **Sem restrição**.
- Para mudar o som/vibração: *Configurações → Apps → Messager → Notificações*
  (canais "Mensagens" e "Chamadas de atenção").
- No celular do filho, deixe o Wi‑Fi ligado com a tela apagada
  (normalmente já é o padrão).

## Compilar

O APK é gerado automaticamente pelo GitHub Actions a cada push
(`.github/workflows/build.yml`). Localmente: `./gradlew assembleRelease`
(precisa do Android SDK).

O APK é assinado com uma chave fixa (`app/messager.keystore`) para que
versões novas instalem por cima das antigas sem perder o histórico.

## Servidor próprio (opcional)

Na tela de configuração dá para trocar `https://ntfy.sh` por um servidor
[ntfy](https://docs.ntfy.sh/install/) próprio. Os dois celulares precisam usar
o mesmo endereço.
