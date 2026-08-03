package br.com.tonus.scheduling;

/**
 * Disparo manual de mensagem livre para todo aluno ativo, pedido pelo ADMIN.
 *
 * Não há fato de domínio por trás deste evento — nada muda na agenda —, e é
 * por isso que ele carrega {@code broadcastId}: um UUID gerado no momento do
 * disparo, usado como {@code reference_key} do log de notificação. A data não
 * serviria: o ADMIN pode legitimamente disparar duas vezes no mesmo dia, e
 * chavear pela data faria o segundo disparo ser engolido pela UNIQUE como se
 * fosse reentrega do primeiro.
 */
public record BroadcastRequested(String broadcastId, String message) {
}
