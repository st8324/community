// 서버 응답이 약속한 모양(01 문서 POST-02·POST-03)인지 확인한다. 아니면 화면은 내용을 쓰지 않고 고정 오류 문장을 보인다 (SEC-10).
// 서버가 틀리게 답해도 화면이 깨지거나 예상 밖의 값을 그리지 않게 하는 방어선이다.

const isObject = (value) => value !== null && typeof value === 'object';

function isPostListItem(item) {
  return (
    isObject(item) &&
    Number.isInteger(item.id) &&
    typeof item.title === 'string' &&
    typeof item.authorName === 'string' &&
    typeof item.createdAt === 'string'
  );
}

export function isPostList(data) {
  return (
    isObject(data) &&
    Array.isArray(data.items) &&
    data.items.every(isPostListItem) &&
    Number.isInteger(data.totalPages) &&
    data.totalPages >= 0
  );
}

export function isPost(data) {
  return (
    isObject(data) &&
    Number.isInteger(data.id) &&
    typeof data.title === 'string' &&
    typeof data.content === 'string' &&
    typeof data.authorName === 'string' &&
    typeof data.createdAt === 'string' &&
    (data.updatedAt === null || typeof data.updatedAt === 'string') &&
    typeof data.canEdit === 'boolean' &&
    typeof data.canDelete === 'boolean'
  );
}
