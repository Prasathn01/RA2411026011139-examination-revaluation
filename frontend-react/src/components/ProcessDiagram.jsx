import { useEffect, useRef, useState } from 'react'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/bpmn-js.css'

// Read-only BPMN diagram (bpmn-js) with the currently active elements outlined.
// The XML comes from the backend (/api/models/{processId}), which serves the deployed file.
export default function ProcessDiagram({ processId, activeElements = [] }) {
  const container = useRef(null)
  const viewer = useRef(null)
  const [xml, setXml] = useState(null)
  const [error, setError] = useState(null)
  const [ready, setReady] = useState(false)   // true once the XML has been drawn

  useEffect(() => {
    fetch(`/api/models/${processId}`).then((r) => (r.ok ? r.text() : Promise.reject(new Error('Diagram not available'))))
      .then(setXml).catch((e) => setError(e.message))
  }, [processId])

  useEffect(() => {
    if (!xml) return
    viewer.current = new NavigatedViewer({ container: container.current })
    viewer.current.importXML(xml).then(() => setReady(true)).catch((e) => setError(e.message))
    return () => { setReady(false); viewer.current.destroy() }
  }, [xml])

  const key = activeElements.join(',')
  useEffect(() => {
    if (!ready) return
    const canvas = viewer.current.get('canvas')
    const registry = viewer.current.get('elementRegistry')
    registry.forEach((el) => canvas.removeMarker(el.id, 'active-element'))
    const present = activeElements.filter((id) => registry.get(id))
    present.forEach((id) => canvas.addMarker(id, 'active-element'))
    // The whole process is too wide to read at "fit", so centre on the innermost active element
    // (the last one: Camunda lists a sub-process before the element waiting inside it).
    const focus = registry.get(present[present.length - 1])
    if (focus) {
      canvas.zoom(0.9)
      const box = canvas.viewbox()
      canvas.viewbox({ x: focus.x + focus.width / 2 - box.width / 2, y: focus.y + focus.height / 2 - box.height / 2, width: box.width, height: box.height })
    } else {
      canvas.zoom('fit-viewport')   // nothing active (finished): show the whole process
    }
  }, [key, ready]) // eslint-disable-line react-hooks/exhaustive-deps

  if (error) return <p className="text-sm text-muted">{error}</p>
  return (
    <>
      <style>{`.active-element .djs-visual > :nth-child(1) { stroke: #24589C !important; stroke-width: 4px !important; fill: #dbeafe !important; }`}</style>
      <div ref={container} className="h-96 w-full rounded-md border border-line bg-white" />
    </>
  )
}
