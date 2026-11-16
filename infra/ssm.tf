# =====================================================================
# Contrato publicado por este serviço
# ---------------------------------------------------------------------
#   /oficina/{env}/execucao/db/jdbc-url    -> cd.yml deste repo
#   /oficina/{env}/execucao/db/secret-arn  -> cd.yml deste repo
# =====================================================================

resource "aws_ssm_parameter" "db_jdbc_url" {
  name        = "${local.prefixo_ssm}/execucao/db/jdbc-url"
  description = "URL JDBC do banco do Execucao Service"
  type        = "String"
  value       = "jdbc:postgresql://${aws_db_instance.este.address}:${aws_db_instance.este.port}/${var.nome_banco}"
  overwrite   = true
}

resource "aws_ssm_parameter" "db_secret_arn" {
  name        = "${local.prefixo_ssm}/execucao/db/secret-arn"
  description = "ARN do secret com usuario e senha do banco do Execucao Service"
  type        = "String"
  value       = aws_secretsmanager_secret.banco.arn
  overwrite   = true
}
