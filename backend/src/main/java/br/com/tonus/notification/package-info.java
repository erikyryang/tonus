/**
 * Avisos ao aluno por WhatsApp. Único módulo que fala com o mundo externo.
 *
 * Depende de registration para resolver nome e telefone do aluno, e dos
 * módulos cujos eventos consome — consumir um evento é referenciar o record
 * que o publicador expõe na raiz, e isso é dependência de compilação como
 * qualquer outra. O acoplamento em tempo de execução continua assíncrono: o
 * caminho de volta não existe, ninguém chama notification.
 *
 * Todas as arestas são acíclicas: nenhum desses módulos conhece notification.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "registration", "scheduling", "billing"})
package br.com.tonus.notification;
