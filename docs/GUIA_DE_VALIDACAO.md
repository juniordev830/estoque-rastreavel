# Roteiro de manutenção — Estoque Rastreável

## O que o código faz

Biblioteca Java 17 de entradas/saídas de estoque em memória, com histórico de movimentações.

## Arquivos de referência

`src/main/java/br/junior/estoque/EstoqueService.java`, `Movimentacao.java` e respectivo teste JUnit.

## Executar verificações

`mvn -B test`.

Use ambiente local e dados fictícios. O CI complementa, mas não substitui a inspeção do comportamento e das entradas de fronteira.

## Testes que não podem regredir

Testar saída acima do saldo, entrada com overflow, nome de produto em branco, snapshots imutáveis e saldo depois de erro.

## Próxima melhoria útil

Adicionar catálogo e persistência transacional; não tratar o serviço em memória como estoque multiusuário.

### Antes de abrir PR

- [ ] Teste ou exemplo reproduzível para a alteração.
- [ ] README atualizado se o comando ou escopo mudou.
- [ ] Dados privados e segredos fora do controle de versão.
- [ ] Revisão em branch, sem merge automático.
