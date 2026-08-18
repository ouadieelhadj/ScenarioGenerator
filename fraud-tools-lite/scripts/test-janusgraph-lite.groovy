import static org.apache.tinkerpop.gremlin.structure.T.*
import org.janusgraph.core.JanusGraphFactory

def root = 'D:/LanaCash/OpenWay/installationOCI/fraud-tools-lite'
def graph = JanusGraphFactory.open(root + '/config/janusgraph-lite.properties')
def g = graph.traversal()

try {
    def mgmt = graph.openManagement()
    if (mgmt.getGraphIndex('byMemberBusinessKey') == null) {
        def memberKey = mgmt.makePropertyKey('memberBusinessKey').dataType(String.class).make()
        mgmt.buildIndex('byMemberBusinessKey', org.apache.tinkerpop.gremlin.structure.Vertex.class)
            .addKey(memberKey).unique().buildCompositeIndex()
        mgmt.commit()
    } else {
        mgmt.rollback()
    }

    g.V().drop().iterate()
    graph.tx().commit()

    def bankA = graph.addVertex(label, 'Member', 'memberBusinessKey', 'BANK_LAB_A', 'displayName', 'Banque laboratoire A')
    def bankB = graph.addVertex(label, 'Member', 'memberBusinessKey', 'BANK_LAB_B', 'displayName', 'Banque laboratoire B')
    def accountA = graph.addVertex(label, 'Account', 'memberId', 'BANK_LAB_A', 'token', 'acct_a_001')
    def cardA1 = graph.addVertex(label, 'Instrument', 'memberId', 'BANK_LAB_A', 'token', 'card_a_001')
    def cardA2 = graph.addVertex(label, 'Instrument', 'memberId', 'BANK_LAB_A', 'token', 'card_a_002')
    def deviceA = graph.addVertex(label, 'Device', 'memberId', 'BANK_LAB_A', 'token', 'dev_a_001')
    def cardB = graph.addVertex(label, 'Instrument', 'memberId', 'BANK_LAB_B', 'token', 'card_b_001')

    bankA.addEdge('OWNS', accountA)
    accountA.addEdge('USES', cardA1)
    accountA.addEdge('USES', cardA2)
    cardA1.addEdge('SEEN_ON', deviceA)
    cardA2.addEdge('SEEN_ON', deviceA)
    bankB.addEdge('OWNS', cardB)
    graph.tx().commit()

    def bankAVertices = g.V().has('memberId', 'BANK_LAB_A').count().next()
    def bankBVertices = g.V().has('memberId', 'BANK_LAB_B').count().next()
    def sharedDeviceCards = g.V().has('token', 'dev_a_001').in('SEEN_ON').count().next()
    def leakedBankB = g.V().has('memberId', 'BANK_LAB_A').both().has('memberId', 'BANK_LAB_B').count().next()

    assert bankAVertices == 4
    assert bankBVertices == 1
    assert sharedDeviceCards == 2
    assert leakedBankB == 0

    println('JANUSGRAPH_LITE_OK')
    println('BANK_LAB_A_VERTICES=' + bankAVertices)
    println('BANK_LAB_B_VERTICES=' + bankBVertices)
    println('SHARED_DEVICE_CARDS=' + sharedDeviceCards)
    println('CROSS_MEMBER_LEAK=' + leakedBankB)
} finally {
    g.close()
    graph.close()
}
