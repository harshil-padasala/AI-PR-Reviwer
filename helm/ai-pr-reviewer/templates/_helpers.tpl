{{/* vim: set filetype=mustache: */}}

{{- define "ai-pr-reviewer.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ai-pr-reviewer.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "ai-pr-reviewer.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ai-pr-reviewer.labels" -}}
helm.sh/chart: {{ include "ai-pr-reviewer.chart" . }}
{{ include "ai-pr-reviewer.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "ai-pr-reviewer.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ai-pr-reviewer.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "ai-pr-reviewer.api.fullname" -}}
{{ include "ai-pr-reviewer.fullname" . }}-api
{{- end -}}

{{- define "ai-pr-reviewer.worker.fullname" -}}
{{ include "ai-pr-reviewer.fullname" . }}-worker
{{- end -}}

{{- define "ai-pr-reviewer.api.selectorLabels" -}}
{{ include "ai-pr-reviewer.selectorLabels" . }}
app.kubernetes.io/component: api
{{- end -}}

{{- define "ai-pr-reviewer.worker.selectorLabels" -}}
{{ include "ai-pr-reviewer.selectorLabels" . }}
app.kubernetes.io/component: worker
{{- end -}}

{{- define "ai-pr-reviewer.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{ default (include "ai-pr-reviewer.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
{{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
DB_URL — points at the Bitnami postgresql subchart's Service when
postgresql.enabled=true, otherwise at the external DB values.
*/}}
{{- define "ai-pr-reviewer.dbUrl" -}}
{{- if .Values.postgresql.enabled -}}
jdbc:postgresql://{{ .Release.Name }}-postgresql:5432/{{ .Values.postgresql.auth.database }}
{{- else -}}
jdbc:postgresql://{{ .Values.database.external.host }}:{{ .Values.database.external.port }}/{{ .Values.database.external.name }}
{{- end -}}
{{- end -}}

{{- define "ai-pr-reviewer.dbUsername" -}}
{{- if .Values.postgresql.enabled -}}
{{ .Values.postgresql.auth.username }}
{{- else -}}
{{ .Values.database.external.username }}
{{- end -}}
{{- end -}}

{{- define "ai-pr-reviewer.dbPassword" -}}
{{- if .Values.postgresql.enabled -}}
{{ .Values.postgresql.auth.password }}
{{- else -}}
{{ .Values.secrets.dbPassword }}
{{- end -}}
{{- end -}}

{{/*
In-cluster URL the worker calls back into the API Service (not the Ingress
— the Ingress is only for GitHub's inbound webhook traffic).
*/}}
{{- define "ai-pr-reviewer.callbackUrl" -}}
http://{{ include "ai-pr-reviewer.api.fullname" . }}:{{ .Values.api.service.port }}/api/reviews/callback
{{- end -}}
