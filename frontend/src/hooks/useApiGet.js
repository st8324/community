import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

/**
 * GET 요청 하나를 불러오는 훅. path가 바뀌면 다시 불러오고, null이면 요청하지 않는다.
 * reloadKey가 바뀌면 같은 path를 다시 불러온다(그동안 이전 결과를 그대로 보여 준다).
 * 결과에 "어느 path의 것인지"를 함께 저장해 두어, path가 바뀐 직후에 이전 결과가 새 주소의 결과처럼 보이지 않게 한다.
 * (효과 안에서 동기적으로 setState하지 않으므로 별도의 로딩 초기화가 필요 없다.)
 */
export function useApiGet(path, reloadKey = 0) {
  const [state, setState] = useState({ path: null, data: null, error: null });

  useEffect(() => {
    if (path === null) {
      return undefined;
    }
    let active = true;
    api.get(path).then(
      (data) => {
        if (active) {
          setState({ path, data, error: null });
        }
      },
      (error) => {
        if (active) {
          setState({ path, data: null, error });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [path, reloadKey]);

  const settled = path !== null && state.path === path;
  return {
    loading: path !== null && !settled,
    data: settled ? state.data : null,
    error: settled ? state.error : null,
  };
}
