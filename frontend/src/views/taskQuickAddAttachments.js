export const MAX_TASK_ATTACHMENT_BYTES = 25 * 1024 * 1024;

export function validateQuickAttachmentFiles(files) {
  const accepted = [];
  const rejected = [];
  for (const file of files || []) {
    if (!file?.size) rejected.push({ file, reason: "附件不能为空" });
    else if (file.size > MAX_TASK_ATTACHMENT_BYTES) rejected.push({ file, reason: "附件不能超过 25 MB" });
    else accepted.push(file);
  }
  return { accepted, rejected };
}

export function formatAttachmentSize(size) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(0)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

export async function uploadQuickAttachments(taskId, attachments, upload, onProgress = () => {}) {
  const uploaded = [];
  for (let index = 0; index < attachments.length; index += 1) {
    const item = attachments[index];
    try {
      await upload(taskId, item.file, percent => onProgress(index, percent), item.id);
      uploaded.push(item.id);
    } catch (error) {
      return { uploaded, remaining: attachments.slice(index), error };
    }
  }
  return { uploaded, remaining: [], error: null };
}
