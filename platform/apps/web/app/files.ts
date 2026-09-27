export type UploadedFile = { id: number; url: string; contentType: string; byteSize: number };

/** Envia um arquivo para a API (E03) e devolve a URL pública. */
export async function uploadFile(file: File, purpose = 'other'): Promise<UploadedFile> {
  const body = new FormData();
  body.append('file', file);
  body.append('purpose', purpose);
  const response = await fetch('/backend/files', { method: 'POST', credentials: 'same-origin', body });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error ?? 'Não foi possível enviar o arquivo');
  return result as UploadedFile;
}
