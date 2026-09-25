# Plano para uma página inicial própria

Atualizado em 24/09/2026. A primeira versão da home própria foi implementada e publicada na VPS. A seleção de região, o acesso à vitrine e o carregamento do conteúdo foram verificados no navegador. Refinamentos visuais e testes completos de compra continuam possíveis em etapas seguintes.

## Objetivo

Criar uma entrada original para o Food Delivery Suite, em português, com identidade própria e uma passagem clara para o catálogo existente. Manter as rotas, o cadastro, a escolha de área, o carrinho e o checkout atuais. A página precisa funcionar mesmo sem dados promocionais cadastrados no painel.

## Situação atual

- A rota `/` usava `/api/v1/react-landing-page`, que responde 503 por exigir a ativação `react_web`. A nova implementação usa configuração e zonas públicas.
- O rodapé e as páginas de catálogo deixaram de consultar essa landing. `/home` continua sendo a vitrine de compra e usa o carregador de configuração da rota `/`, agora independente da landing bloqueada.
- Algumas páginas de catálogo usavam a landing apenas para a imagem de compartilhamento. Agora usam uma imagem estática local.

## Proposta visual

Usar a direção acolhedora e gastronômica aprovada na comparação com o Figma, mas compor a página do zero: tipografia legível, base clara, cor principal do produto, fotografias ou ilustrações com tratamento consistente e bastante espaço entre seções. Usar somente imagens locais com origem e permissão verificadas ou artes produzidas para o projeto. Nenhum texto, imagem ou estrutura da landing bloqueada será requisito para a nova home.

### Ordem das seções

1. **Cabeçalho enxuto:** marca, link para explorar o cardápio, entrar/cadastrar-se e carrinho quando aplicável. No celular, navegação compacta.
2. **Destaque principal:** frase sugerida **“Seu próximo prato favorito começa aqui.”**, apoio “Descubra restaurantes e sabores para pedir onde você estiver.”, botão **“Explorar restaurantes”** e controle **“Escolher minha região”**. A escolha usa o fluxo de localização e zonas já existente; após selecionar uma zona válida, leva a `/home`.
3. **Como funciona:** três passos curtos: escolher a região, encontrar o prato e acompanhar o pedido. Ilustrações próprias e discretas.
4. **Descubra o que pedir:** prévia de categorias e restaurantes reais quando a zona estiver definida. Sem zona, mostrar categorias ilustrativas sem preços ou promessas de disponibilidade, com chamada para escolher a região. Se a API estiver vazia, mostrar uma mensagem útil e o botão de escolha.
5. **Duas faixas editoriais:** “Para cada vontade, um sabor” levando à exploração do cardápio e “Seu negócio também pode estar aqui” levando ao cadastro de restaurante. Substituem as faixas vazias atuais com imagens que combinem com a mensagem.
6. **Seja entregador parceiro:** bloco com imagem própria, benefício descrito sem alegações não confirmadas e botão para a inscrição de entregador já existente no backend.
7. **Aplicativos:** explicar que as versões web dos apps de cliente, restaurante e entregador podem ser abertas pelos links atuais. O login dos apps de restaurante e entregador está bloqueado por ativação na VPS, conforme `AUDITORIA_IMPEDIMENTOS.md`; não apresentá-los como operacionais antes de resolver isso. Mostrar botões de loja móvel somente quando os links reais de App Store/Google Play estiverem configurados; rótulos em português.
8. **Rodapé completo:** newsletter, contato em Fortaleza, CE, links internos válidos, ajuda e suporte, políticas e “Copyright © Axyon Software House”. O texto e as imagens essenciais ficam no próprio frontend ou vêm de `/api/v1/config`, sem depender da landing bloqueada.

### Versão móvel

Hero em uma coluna, CTA e escolha de região visíveis sem rolagem excessiva; cards em lista/carrossel acessível; faixas com imagem acima do texto; botões grandes; imagens leves e sem corte do assunto principal.

## Dados e navegação

| Necessidade | Fonte ou destino previsto |
| --- | --- |
| Nome, marca e configurações | `/api/v1/config` |
| Áreas de entrega | `/api/v1/zone/list` e fluxo atual de seleção de zona |
| Categorias e cozinhas | `/api/v1/categories` e `/api/v1/cuisine` |
| Restaurantes e produtos após escolha de zona | APIs já usadas por `/home`, incluindo `/api/v1/restaurants/popular` e `/api/v1/products/popular` |
| Comprar | `/home`, preservando busca, detalhes, carrinho e checkout existentes |
| Restaurante parceiro | `/restaurant-registration` ou destino de inscrição configurado no backend, após verificar o fluxo publicado |
| Entregador parceiro | `/deliveryman/apply` na API, após verificar o fluxo publicado |
| Suporte | `/help-and-support` |
| Imagem de compartilhamento | Arquivo estático próprio em HTTPS |

Não exigir conteúdo do endpoint `/api/v1/react-landing-page`. As APIs de catálogo podem variar conforme zona, então a página deve tratar explicitamente ausência de zona, resposta vazia, falha temporária e carregamento.

## Execução proposta

1. **Base funcional:** criar componentes independentes para `/`; manter `/home` como vitrine de compra; separar seu carregamento inicial da landing bloqueada. Remover a consulta da landing do rodapé e dos metadados das demais páginas.
2. **Conteúdo e visual:** produzir as imagens necessárias, aplicar o layout responsivo, revisar todos os textos em português e usar chamadas para ação com destino real. Evitar exibir serviços ou benefícios ainda não validados.
3. **Validação local:** testar desktop e celular, seleção de região, navegação para restaurante, produto, login, carrinho, checkout, suporte e inscrições. Confirmar que `/`, `/home` e demais páginas não consultam `/api/v1/react-landing-page`.
4. **Publicação controlada:** compilar, publicar somente o frontend necessário, verificar páginas e console do navegador por HTTPS na VPS e manter caminho de reversão para a versão anterior.

## Critérios de aceite

- `/` abre de modo estável, com texto e imagens completos, mesmo quando não há campanhas ou uma zona escolhida.
- A pessoa consegue escolher a região e chegar ao catálogo; nenhum link ou botão termina em página vazia.
- O rodapé funciona nas páginas de compra e suporte sem a ativação da landing.
- Não há requisição para `/api/v1/react-landing-page` na navegação normal do site.
- Desktop e celular não mostram áreas de imagem em branco, loops de recarga, erro de execução ou conteúdo essencial em inglês.
- Os endereços dos aplicativos Flutter e do painel administrativo continuam acessíveis; a operação autenticada dos módulos sujeitos a ativação deve ser validada à parte.
