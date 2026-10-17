"""Exercise a disposable demo installation. Never point at a real business database."""
import argparse
import concurrent.futures
import datetime
import decimal
import json
import time
import urllib.request
import urllib.error
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:9528')
    parser.add_argument('--allow-demo-writes', action='store_true', required=True)
    args = parser.parse_args()
    origin = args.url.rstrip('/')
    run = uuid.uuid4().hex[:12]
    password = uuid.uuid4().hex

    def call(route, token=None, data=None, ok=True, raw=None, mime=None):
        headers = {'Content-Type': mime or 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(origin + '/api' + route, headers=headers,
            data=raw if raw is not None else (None if data is None else json.dumps(data).encode()))
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)
        except urllib.error.HTTPError as exc:
            result = json.loads(exc.read())
        if ok:
            assert result.get('code') == 200, f'{route}: {result.get("message")}'
            return result.get('data')
        assert result.get('code') != 200, f'{route}: unexpected permission grant'

    def login(account, secret='123456'):
        return call('/auth/login', data={'account': account, 'password': secret})['token']

    admin, other, auditor = login('admin'), login('20210001'), login('auditor')
    login('club001')
    name = 'release-' + run
    code = call('/auth/send-code', data={'target': name, 'bizType': 'REGISTER'})
    call('/auth/register', data={'account': name, 'password': password, 'confirmPassword': password,
        'role': 'STUDENT', 'realName': '发布验证同学', 'identityNo': name, 'college': '测试学院', 'code': code})
    student = login(name, password)
    order_form = {'projectId': 1, 'amount': 12.34, 'requestKey': run + '-order'}
    call('/orders', student, order_form, ok=False)
    identity = call('/user/identity', student, {'identityNo': name, 'realName': '发布验证同学',
        'college': '测试学院', 'acknowledged': True})
    profile = call('/user/profile', student)
    assert profile['authStatus'] == 2 and profile['identityTestOnly'] is True
    before = decimal.Decimal(str(call('/projects/1', student)['currentAmount']))
    order = call('/orders', student, order_form)
    oid = order['orderId']
    assert call('/orders', student, order_form)['orderId'] == oid
    call(f'/orders/{oid}/mock-pay', other, {'result': 'SUCCESS'}, ok=False)
    assert call(f'/orders/{oid}/mock-pay', student, {'result': 'FAILED'})['status'] == 'PAY_FAILED'
    paid = call(f'/orders/{oid}/mock-pay', student, {'result': 'SUCCESS'})
    assert paid['status'] == 'WAIT_CONFIRM' and paid['voucherFileId']
    assert call(f'/orders/{oid}/mock-pay', student, {'result': 'SUCCESS'})['voucherFileId'] == paid['voucherFileId']
    confirm = {'orderId': oid, 'result': 'REJECT', 'checkedAmount': 12.34,
        'voucherFileId': paid['voucherFileId'], 'opinion': '发布验证：补充测试说明', 'requestKey': run + '-reject'}
    call('/finance/confirmations', admin, confirm)
    paid = call(f'/orders/{oid}/resubmit', student, {'remark': '测试说明已补充'})
    confirm.update(result='PASS', voucherFileId=paid['voucherFileId'], opinion='测试核对通过', requestKey=run + '-confirm')
    call('/finance/confirmations', auditor, confirm, ok=False)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        confirmations = list(pool.map(lambda _: call('/finance/confirmations', admin, confirm), range(2)))
    did = confirmations[0]['donationId']
    assert confirmations[1]['donationId'] == did
    assert decimal.Decimal(str(call('/projects/1', student)['currentAmount'])) == before + decimal.Decimal('12.34')
    refund_form = {'donationId': did, 'reason': '发布验证：全额测试退款', 'requestKey': run + '-refund1'}
    call('/refunds', other, refund_form, ok=False)
    refund = call('/refunds', student, refund_form)
    rid = refund['refundId']
    call(f'/finance/refunds/{rid}/audit', admin, {'result': 'REJECT', 'opinion': '发布验证：原因需补充'})
    refund_form.update(reason='发布验证：补充原因后全额退款', requestKey=run + '-refund2')
    refund = call('/refunds', student, refund_form)
    rid = refund['refundId']
    call(f'/finance/refunds/{rid}/audit', admin, {'result': 'PASS', 'opinion': '发布验证通过'})
    assert decimal.Decimal(str(call('/projects/1', student)['currentAmount'])) == before + decimal.Decimal('12.34')
    boundary = '----release' + run
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="test-refund.txt"\r\n'
        'Content-Type: text/plain\r\n\r\nInternal test refund receipt. No actual funds transferred.\r\n'
        f'--{boundary}--\r\n').encode()
    receipt = call('/file/upload', admin, raw=raw, mime='multipart/form-data; boundary=' + boundary)
    complete = {'voucherFileId': receipt['fileId'], 'completeTime': datetime.datetime.now().strftime('%Y-%m-%dT%H:%M:%S')}
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call(f'/finance/refunds/{rid}/complete', admin, complete), range(2)))
    assert decimal.Decimal(str(call('/projects/1', student)['currentAmount'])) == before
    detail = call('/refunds/' + str(rid), student)
    print(json.dumps({'result': 'PASS', 'checks': ['four-demo-logins', 'fresh-registration', 'test-identity-no-upload',
        'unverified-order-rejected', 'failure-retry', 'automatic-receipt', 'finance-rejection-resubmission',
        'concurrent-idempotent-confirmation', 'refund-rejection-reapplication', 'approval-does-not-deduct',
        'concurrent-idempotent-refund-completion', 'ownership-and-auditor-write-denied', 'net-amount-restored']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
