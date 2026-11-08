# Estoque Rastreável

Biblioteca de domínio em **Java 17** para controlar entradas, saídas, produtos, fornecedores, depósitos, reservas, inventário, lotes com validade e relatórios.

O serviço original `EstoqueService` e seu modelo `Movimentacao` foram preservados. As novas funcionalidades são módulos adicionais, sem dependências de runtime além do JDK.

## Funcionalidades

| Módulo | Recursos |
| --- | --- |
| `EstoqueService` | Entradas e saídas básicas, saldo não negativo, histórico e proteção contra overflow |
| `CatalogoProdutos` | SKU normalizado, nome, categoria, unidade, mínimo/máximo, custo, ativação, fornecedores e auditoria |
| `EstoqueLocalizado` | Depósitos, entradas, retiradas, transferências atômicas, reservas, atendimento, cancelamento, contagem física e histórico |
| `LotesValidade` | Recebimento de lotes, validade, consumo FEFO (primeiro a vencer, primeiro a sair), alertas e rastreabilidade |
| `RelatoriosEstoque` | Valor estimado, estoque disponível, posições críticas, sugestões de reposição, CSV seguro e Markdown |

## Requisitos e execução

JDK 17 e Maven. Execute na raiz:

```bash
mvn -B test
```

O CI executa os testes em cada push e pull request. A suíte utiliza JUnit 5, `Clock` fixo para datas determinísticas e verifica fluxos normais, falhas, atomicidade, histórico, segurança de CSV e casos de borda.

## Exemplo de uso

```java
import br.junior.estoque.*;
import java.math.BigDecimal;

var catalogo = new CatalogoProdutos();
catalogo.cadastrarProduto("MOUSE-01", "Mouse USB", "Periféricos",
        CatalogoProdutos.Unidade.UNIDADE, 5, 20, new BigDecimal("35.90"));

var estoque = new EstoqueLocalizado(catalogo);
estoque.cadastrarLocal("DEP", "Depósito principal");
estoque.cadastrarLocal("LOJA", "Ponto de venda");
estoque.receber("MOUSE-01", "DEP", 15, "NF-100");
estoque.transferir("MOUSE-01", "DEP", "LOJA", 5, "TR-10");

var reserva = estoque.reservar("MOUSE-01", "LOJA", 2, "PED-001");
estoque.atenderReserva(reserva.id());

var relatorios = new RelatoriosEstoque(catalogo, estoque);
System.out.println(relatorios.resumo());
System.out.println(relatorios.exportarPosicoesCsv());
```

## Regras e segurança

- Quantidades devem ser positivas; saldos não podem ficar negativos.
- Reservas diminuem o **disponível**, mas não o físico até o atendimento.
- Transferências verificam origem e destino antes de alterar ambos.
- Ajustes de inventário não podem reduzir o saldo abaixo das reservas ativas.
- Entradas em produtos e depósitos inativos são bloqueadas.
- O consumo de lotes usa validade crescente e rejeita retirada acima do saldo válido.
- Exportações CSV colocam campos entre aspas, duplicam aspas internas e mitigam fórmulas.
- Eventos e snapshots retornados são imutáveis para os consumidores.
- Custos são decimais (`BigDecimal`), evitando arredondamento binário.

## Limitações importantes

**Os módulos `EstoqueLocalizado` e `LotesValidade` são independentes.** Para uma aplicação que use ambos, a sincronização transacional entre movimentos físicos e lotes deverá ser implementada em uma camada de aplicação; não presuma que o saldo de lotes se atualiza automaticamente quando um depósito recebe ou transfere itens.

Os dados permanecem **em memória** e desaparecem ao encerrar o processo. Não há banco de dados, API HTTP, autenticação, interface, multiempresa nem persistência. Os serviços sincronizam operações dentro de cada instância, mas não oferecem transações distribuídas entre módulos. O valor de estoque é uma estimativa baseada no custo cadastrado, não uma avaliação contábil completa.

## Organização

- `src/main/java/br/junior/estoque/EstoqueService.java` — domínio original.
- `src/main/java/br/junior/estoque/Movimentacao.java` — registro original.
- `src/main/java/br/junior/estoque/CatalogoProdutos.java` — produtos, fornecedores e auditoria.
- `src/main/java/br/junior/estoque/EstoqueLocalizado.java` — depósitos, reservas e inventário.
- `src/main/java/br/junior/estoque/LotesValidade.java` — lotes e validade.
- `src/main/java/br/junior/estoque/RelatoriosEstoque.java` — indicadores e exportações.
- `src/test/java/br/junior/estoque/` — testes JUnit.

Use dados fictícios nos exemplos. Não versionar credenciais nem dados pessoais. Licença ainda não definida.
